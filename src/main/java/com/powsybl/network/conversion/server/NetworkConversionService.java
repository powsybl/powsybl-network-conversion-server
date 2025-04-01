/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.cases.datasource.CaseDataSourceClient;
import com.powsybl.cgmes.conversion.export.CgmesExportContext;
import com.powsybl.cgmes.conversion.export.StateVariablesExport;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeDeserializer;
import com.powsybl.commons.report.ReportNodeJsonModule;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.iidm.network.*;
import com.powsybl.network.conversion.server.dto.*;
import com.powsybl.network.conversion.server.elasticsearch.EquipmentInfosService;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.powsybl.network.conversion.server.NetworkConversionConstants.DELIMITER;
import static com.powsybl.network.conversion.server.NetworkConversionConstants.REPORT_API_VERSION;
import static com.powsybl.network.conversion.server.dto.EquipmentInfos.getEquipmentTypeName;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@Service
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
public class NetworkConversionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionService.class);

    public static final Set<IdentifiableType> TYPES_FOR_INDEXING = Set.of(
            IdentifiableType.SUBSTATION,
            IdentifiableType.VOLTAGE_LEVEL,
            IdentifiableType.HVDC_LINE,
            IdentifiableType.LINE,
            IdentifiableType.TIE_LINE,
            IdentifiableType.TWO_WINDINGS_TRANSFORMER,
            IdentifiableType.THREE_WINDINGS_TRANSFORMER,
            IdentifiableType.GENERATOR,
            IdentifiableType.BATTERY,
            IdentifiableType.LOAD,
            IdentifiableType.SHUNT_COMPENSATOR,
            IdentifiableType.DANGLING_LINE,
            IdentifiableType.STATIC_VAR_COMPENSATOR,
            IdentifiableType.HVDC_CONVERTER_STATION);

    private RestTemplate caseServerRest;

    private RestTemplate geoDataServerRest;

    private RestTemplate reportServerRest;

    private final NetworkStoreService networkStoreService;

    private final EquipmentInfosService equipmentInfosService;

    private final NetworkConversionExecutionService networkConversionExecutionService;

    private final NotificationService notificationService;

    private final ImportExportExecutionService importExportExecutionService;
    private final NetworkConversionObserver networkConversionObserver;

    private final ObjectMapper objectMapper;

    public NetworkConversionService(@Value("${powsybl.services.case-server.base-uri:http://case-server/}") String caseServerBaseUri,
                                    @Value("${gridsuite.services.geo-data-server.base-uri:http://geo-data-server/}") String geoDataServerBaseUri,
                                    @Value("${gridsuite.services.report-server.base-uri:http://report-server}") String reportServerURI,
                                    NetworkStoreService networkStoreService,
                                    EquipmentInfosService equipmentInfosService,
                                    NetworkConversionExecutionService networkConversionExecutionService,
                                    NotificationService notificationService,
                                    NetworkConversionObserver networkConversionObserver,
                                    ImportExportExecutionService importExportExecutionService) {
        this.networkStoreService = networkStoreService;
        this.equipmentInfosService = equipmentInfosService;
        this.networkConversionExecutionService = networkConversionExecutionService;
        this.notificationService = notificationService;
        this.networkConversionObserver = networkConversionObserver;
        this.importExportExecutionService = importExportExecutionService;

        RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
        caseServerRest = restTemplateBuilder.build();
        caseServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri));

        geoDataServerRest = restTemplateBuilder.build();
        geoDataServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(geoDataServerBaseUri));

        reportServerRest = restTemplateBuilder.build();
        reportServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(reportServerURI));

        objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.registerModule(new ReportNodeJsonModule());
        objectMapper.setInjectableValues(new InjectableValues.Std().addValue(ReportNodeDeserializer.DICTIONARY_VALUE_ID, null));
    }

    static EquipmentInfos toEquipmentInfos(Identifiable<?> i, UUID networkUuid, String variantId) {
        return EquipmentInfos.builder()
            .networkUuid(networkUuid)
            .variantId(variantId)
            .id(i.getId())
            .name(i.getNameOrId())
            .type(getEquipmentTypeName(i))
            .voltageLevels(EquipmentInfos.getVoltageLevelsInfos(i))
            .substations(EquipmentInfos.getSubstationsInfos(i))
            .build();
    }

    void importCaseAsynchronously(UUID caseUuid, String variantId, UUID reportUuid, String caseFormat, Map<String, Object> importParameters, String receiver) {
        notificationService.emitCaseImportStart(caseUuid, variantId, reportUuid, caseFormat, importParameters, receiver);
    }

    Map<String, String> getDefaultImportParameters(CaseInfos caseInfos) {
        Importer importer = Importer.find(caseInfos.getFormat());
        Map<String, String> defaultValues = new HashMap<>();
        importer.getParameters()
                .stream()
                .forEach(parameter -> defaultValues.put(parameter.getName(),
                        parameter.getDefaultValue() != null ? parameter.getDefaultValue().toString() : ""));
        return defaultValues;
    }

    @Bean
    Consumer<Message<UUID>> consumeCaseImportStart() {
        return message -> {
            UUID caseUuid = message.getPayload();
            String variantId = message.getHeaders().get(NotificationService.HEADER_VARIANT_ID, String.class);
            String reportUuidStr = message.getHeaders().get(NotificationService.HEADER_REPORT_UUID, String.class);
            UUID reportUuid = reportUuidStr != null ? UUID.fromString(reportUuidStr) : null;
            String receiver = message.getHeaders().get(NotificationService.HEADER_RECEIVER, String.class);
            Map<String, Object> rawParameters = (Map<String, Object>) message.getHeaders().get(NotificationService.HEADER_IMPORT_PARAMETERS);
            // String longer than 1024 bytes are converted to com.rabbitmq.client.LongString (https://docs.spring.io/spring-amqp/docs/3.0.0/reference/html/#message-properties-converters)
            Map<String, Object> changedImportParameters = new HashMap<>();
            if (rawParameters != null) {
                rawParameters.forEach((key, value) -> changedImportParameters.put(key, value.toString()));
            }

            Map<String, String> allImportParameters = new HashMap<>();
            changedImportParameters.forEach((k, v) -> allImportParameters.put(k, v.toString()));
            CaseInfos caseInfos = getCaseInfos(caseUuid);
            getDefaultImportParameters(caseInfos).forEach(allImportParameters::putIfAbsent);

            try {
                NetworkInfos networkInfos = importCase(caseUuid, variantId, reportUuid, caseInfos.getFormat(), changedImportParameters);
                notificationService.emitCaseImportSucceeded(networkInfos, caseInfos.getName(), caseInfos.getFormat(), receiver, allImportParameters);
            } catch (Exception e) {
                throw NetworkConversionException.createFailedCaseImport(e);
            }
        };
    }

    private NetworkInfos importCaseExec(UUID caseUuid, String variantId, UUID reportUuid, String caseFormat, Map<String, Object> importParameters) {
        CaseDataSourceClient dataSource = new CaseDataSourceClient(caseServerRest, caseUuid);
        ReportNode rootReport = ReportNode.NO_OP;
        ReportNode reporter = ReportNode.NO_OP;
        if (reportUuid != null) {
            String reporterId = "Root";
            rootReport = ReportNode.newRootReportNode()
                    .withMessageTemplate(reporterId, reporterId)
                    .build();

            String subReporterId = "Import Case : " + dataSource.getBaseName();
            reporter = rootReport.newReportNode()
                    .withMessageTemplate(subReporterId, subReporterId)
                    .add();
        }

        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        Network network;
        ReportNode finalReporter = reporter;
        if (!importParameters.isEmpty()) {
            Properties importProperties = new Properties();
            importProperties.putAll(importParameters);
            network = networkConversionObserver.observeImport(caseFormat, () -> networkStoreService.importNetwork(dataSource, finalReporter, importProperties, false));
        } else {
            network = networkConversionObserver.observeImport(caseFormat, () -> networkStoreService.importNetwork(dataSource, finalReporter, false));
        }
        UUID networkUuid = networkStoreService.getNetworkUuid(network);
        LOGGER.trace("Import network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        saveNetwork(network, networkUuid, variantId, rootReport, reportUuid);
        return new NetworkInfos(networkUuid, network.getId());
    }

    public NetworkInfos importCase(UUID caseUuid, String variantId, UUID reportUuid, String caseFormat, Map<String, Object> importParameters) {
        try {
            return importExportExecutionService.supplyAsync(() -> importCaseExec(caseUuid, variantId, reportUuid, caseFormat, importParameters)).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof NetworkConversionException exception) {
                throw exception;
            }
            throw NetworkConversionException.createFailedCaseImport(e);
        }
    }

    private void saveNetwork(Network network, UUID networkUuid, String variantId, ReportNode reporter, UUID reportUuid) {
        CompletableFuture<Void> saveInParallel;
        if (reportUuid == null) {
            saveInParallel = CompletableFuture.allOf(
                networkConversionExecutionService.runAsync(() -> storeNetworkInitialVariants(network, networkUuid, variantId)),
                networkConversionExecutionService.runAsync(() -> insertEquipmentIndexes(network, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID))
            );
        } else {
            saveInParallel = CompletableFuture.allOf(
                networkConversionExecutionService.runAsync(() -> storeNetworkInitialVariants(network, networkUuid, variantId)),
                networkConversionExecutionService.runAsync(() -> sendReport(networkUuid, reporter, reportUuid)),
                networkConversionExecutionService.runAsync(() -> insertEquipmentIndexes(network, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID))
            );
        }
        try {
            saveInParallel.get();
        } catch (InterruptedException | ExecutionException e) {
            undoSaveNetwork(networkUuid, reportUuid);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw NetworkConversionException.createFailedNetworkSaving(networkUuid, e);
        }
    }

    private void undoSaveNetwork(UUID networkUuid, UUID reportUuid) {
        CompletableFuture<Void> deleteInParallel;
        if (reportUuid == null) {
            deleteInParallel = CompletableFuture.allOf(
                networkConversionExecutionService.runAsync(() -> networkStoreService.deleteNetwork(networkUuid)),
                networkConversionExecutionService.runAsync(() -> equipmentInfosService.deleteAllOnInitialVariant(networkUuid))
            );
        } else {
            deleteInParallel = CompletableFuture.allOf(
                networkConversionExecutionService.runAsync(() -> networkStoreService.deleteNetwork(networkUuid)),
                networkConversionExecutionService.runAsync(() -> deleteReport(reportUuid)),
                networkConversionExecutionService.runAsync(() -> equipmentInfosService.deleteAllOnInitialVariant(networkUuid))
            );
        }
        try {
            deleteInParallel.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean doesNetworkExist(UUID networkUuid) {
        try {
            networkStoreService.getNetwork(networkUuid);
            return true;
        } catch (PowsyblException e) {
            return false;
        }

    }

    private Network getNetwork(UUID networkUuid) {
        try {
            return networkStoreService.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    private ExportNetworkInfos exportNetworkExec(UUID networkUuid, String variantId, String fileName,
        String format, Map<String, Object> formatParameters) throws IOException {
        if (!Exporter.getFormats().contains(format)) {
            throw NetworkConversionException.createFormatUnsupported(format);
        }
        MemDataSource memDataSource = new MemDataSource();
        Properties exportProperties = null;
        if (formatParameters != null) {
            exportProperties = new Properties();
            exportProperties.putAll(formatParameters);
        }

        Network network = getNetwork(networkUuid);
        if (variantId != null) {
            if (network.getVariantManager().getVariantIds().contains(variantId)) {
                network.getVariantManager().setWorkingVariant(variantId);
            } else {
                throw NetworkConversionException.createVariantIdUnknown(variantId);
            }
        }

        network.write(format, exportProperties, memDataSource);

        Set<String> listNames = memDataSource.listNames(".*");
        String fileOrNetworkName = fileName != null ? fileName : getNetworkName(network, variantId);
        byte[] networkData;

        if (listNames.size() == 1) {
            fileOrNetworkName += listNames.toArray()[0];
            networkData = memDataSource.getData(listNames.toArray()[0].toString());
        } else {
            fileOrNetworkName += ".zip";
            networkData = createZipFile(listNames.toArray(new String[0]), memDataSource).toByteArray();
        }
        long networkSize = network.getBusView().getBusStream().count();
        return new ExportNetworkInfos(fileOrNetworkName, networkData, networkSize);
    }

    public ExportNetworkInfos exportNetwork(UUID networkUuid, String variantId, String fileName,
        String format, Map<String, Object> formatParameters) {
        try {
            return importExportExecutionService.supplyAsync(() -> {
                try {
                    return exportNetworkExec(networkUuid, variantId, fileName, format, formatParameters);
                } catch (IOException e) {
                    throw NetworkConversionException.createFailedCaseExport(e);
                }
            }).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof NetworkConversionException exception) {
                throw exception;
            }
            throw NetworkConversionException.createFailedCaseExport(e);
        }
    }

    private String getNetworkName(Network network, String variantId) {
        String networkName = network.getNameOrId();
        networkName += "_" + (variantId == null ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId);
        return networkName;
    }

    ByteArrayOutputStream createZipFile(String[] listNames, MemDataSource dataSource) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String listName : listNames) {
                ZipEntry entry = new ZipEntry(listName);
                zos.putNextEntry(entry);
                zos.write(dataSource.getData(listName));
                zos.closeEntry();
            }
        }
        return baos;
    }

    Map<String, ImportExportFormatMeta> getAvailableFormat() {
        Collection<String> formatsIds = Exporter.getFormats();
        Map<String, ImportExportFormatMeta> ret = formatsIds.stream().map(formatId -> {
            Exporter exporter = Exporter.find(formatId);
            List<ParamMeta> paramsMeta = exporter.getParameters()
                    .stream()
                    .filter(pp -> pp.getScope().equals(ParameterScope.FUNCTIONAL))
                    .map(pp -> new ParamMeta(pp.getName(), pp.getType(), pp.getDescription(), pp.getDefaultValue(), pp.getPossibleValues()))
                    .collect(Collectors.toList());
            return Pair.of(formatId, new ImportExportFormatMeta(formatId, paramsMeta));
        }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        return ret;
    }

    ImportExportFormatMeta getCaseImportParameters(UUID caseUuid) {
        CaseInfos caseInfos = getCaseInfos(caseUuid);
        Importer importer = Importer.find(caseInfos.getFormat());
        List<ParamMeta> paramsMeta = importer.getParameters()
                .stream()
                .filter(pp -> pp.getScope().equals(ParameterScope.FUNCTIONAL))
                .map(pp -> new ParamMeta(pp.getName(), pp.getType(), pp.getDescription(), pp.getDefaultValue(), pp.getPossibleValues()))
                .collect(Collectors.toList());
        return new ImportExportFormatMeta(caseInfos.getFormat(), paramsMeta);
    }

    CaseInfos getCaseInfos(UUID caseUuid) {
        return caseServerRest.getForEntity("/v1/cases/" + caseUuid + "/infos", CaseInfos.class).getBody();
    }

    void setCaseServerRest(RestTemplate caseServerRest) {
        this.caseServerRest = Objects.requireNonNull(caseServerRest, "caseServerRest can't be null");
    }

    void setGeoDataServerRest(RestTemplate geoDataServerRest) {
        this.geoDataServerRest = Objects.requireNonNull(geoDataServerRest, "geoDataServerRest can't be null");
    }

    public ExportNetworkInfos exportCgmesSv(UUID networkUuid) throws XMLStreamException {
        Network network = getNetwork(networkUuid);

        Properties properties = new Properties();
        properties.put("iidm.import.cgmes.profile-used-for-initial-state-values", "SV");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLStreamWriter writer = null;

        try {
            writer = XmlUtil.initializeWriter(true, "    ", outputStream);
            StateVariablesExport.write(network, writer, createContext(network));
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        long networkSize = network.getBusView().getBusStream().count();
        return new ExportNetworkInfos(network.getNameOrId(), outputStream.toByteArray(), networkSize);
    }

    private static CgmesExportContext createContext(Network network) {
        CgmesExportContext context = new CgmesExportContext();
        context.setScenarioTime(network.getCaseDate());
        context.addIidmMappings(network);
        return context;
    }

    NetworkInfos importCgmesCase(UUID caseUuid, List<BoundaryInfos> boundaries) {
        String caseFormat = "CGMES";
        if (CollectionUtils.isEmpty(boundaries)) {  // no boundaries given, standard import
            return importCase(caseUuid, null, UUID.randomUUID(), caseFormat, new HashMap<>());
        } else {  // import using the given boundaries
            CaseDataSourceClient dataSource = new CgmesCaseDataSourceClient(caseServerRest, caseUuid, boundaries);
            Network network = networkConversionObserver.observeImport(caseFormat, () -> networkStoreService.importNetwork(dataSource));
            UUID networkUuid = networkStoreService.getNetworkUuid(network);
            return new NetworkInfos(networkUuid, network.getId());
        }
    }

    private void storeNetworkInitialVariants(Network network, UUID networkUuid, String variantId) {
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        try {
            networkStoreService.flush(network);
            if (variantId != null) {
                // cloning network initial variant into variantId
                networkStoreService.cloneVariant(networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID, variantId);
            }
        } finally {
            LOGGER.trace("Flush network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private void insertEquipmentIndexes(Network network, UUID networkUuid, String variantId) {
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        try {
            equipmentInfosService.addAll(new ArrayList<>(getEquipmentInfos(network, networkUuid, variantId).values()));
        } finally {
            LOGGER.trace("Indexation network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private Map<String, EquipmentInfos> getEquipmentInfos(Network network, UUID networkUuid, String variantId) {
        return TYPES_FOR_INDEXING.stream()
                .flatMap(network::getIdentifiableStream)
                .collect(Collectors.toMap(Identifiable::getId, equipment -> toEquipmentInfos(equipment, networkUuid, variantId)));
    }

    private void sendReport(UUID networkUuid, ReportNode reportNode, UUID reportUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resourceUrl = DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER + reportUuid.toString();
        var uriBuilder = UriComponentsBuilder.fromPath(resourceUrl);
        try {
            reportServerRest.exchange(uriBuilder.toUriString(), HttpMethod.PUT, new HttpEntity<>(objectMapper.writeValueAsString(reportNode), headers), ReportNode.class);
        } catch (JsonProcessingException error) {
            throw new PowsyblException("error creating report", error);
        } finally {
            LOGGER.trace("Save reports for network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private void deleteReport(UUID reportUuid) {
        var resourceUrl = DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER + reportUuid.toString();
        reportServerRest.delete(resourceUrl);
    }

    public void setReportServerRest(RestTemplate reportServerRest) {
        this.reportServerRest = Objects.requireNonNull(reportServerRest, "caseServerRest can't be null");
    }

    public void reindexAllEquipments(UUID networkUuid) {
        Network initialNetwork = getNetwork(networkUuid);

        // delete all network equipments infos. deleting a lot of documents in ElasticSearch is slow, we delete the index instead in maintenance script before reindexing
        deleteAllEquipmentInfosByNetworkUuid(networkUuid);

        // recreate all equipments infos
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        try {
            // save initial variant infos
            Map<String, EquipmentInfos> initialVariantEquipmentInfos = getEquipmentInfos(initialNetwork, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
            equipmentInfosService.addAll(new ArrayList<>(initialVariantEquipmentInfos.values()));

            // get variant ids without the initial that is already processed and is the reference
            List<String> variantIds = initialNetwork.getVariantManager()
                    .getVariantIds()
                    .stream()
                    .filter(variantId -> !variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID))
                    .toList();

            for (String variantId : variantIds) {
                // switch to working variant
                // get a new network (and associated cache) to avoid loading all variants in the same cache
                Network currentNetwork = getNetwork(networkUuid);
                currentNetwork.getVariantManager().setWorkingVariant(variantId);

                // get current variant infos and change variantId for comparisons
                Map<String, EquipmentInfos> currentVariantEquipmentInfos = getEquipmentInfos(currentNetwork, networkUuid, variantId);
                currentVariantEquipmentInfos.values().forEach(equipmentInfos ->
                        equipmentInfos.setVariantId(VariantManagerConstants.INITIAL_VARIANT_ID));

                List<EquipmentInfos> createdEquipmentInfos = currentVariantEquipmentInfos.entrySet().stream()
                        .filter(entry -> !initialVariantEquipmentInfos.containsKey(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .toList();

                // check if there are changes between current and initial variants infos
                List<EquipmentInfos> modifiedEquipmentInfos = currentVariantEquipmentInfos.entrySet().stream()
                        .filter(entry -> {
                            EquipmentInfos initialEquipmentInfo = initialVariantEquipmentInfos.get(entry.getKey());
                            return initialEquipmentInfo != null && !Objects.equals(initialEquipmentInfo, entry.getValue());
                        })
                        .map(Map.Entry::getValue)
                        .toList();

                List<TombstonedEquipmentInfos> tombstonedEquipmentInfos = initialVariantEquipmentInfos.keySet().stream()
                        .filter(equipmentInfos -> !currentVariantEquipmentInfos.containsKey(equipmentInfos))
                        .map(equipmentInfos -> TombstonedEquipmentInfos.builder()
                                .networkUuid(networkUuid)
                                .variantId(variantId)
                                .id(equipmentInfos)
                                .build())
                        .collect(Collectors.toList());

                // set variantId for saved documents after comparison
                createdEquipmentInfos.forEach(equipmentInfos ->
                        equipmentInfos.setVariantId(variantId));
                modifiedEquipmentInfos.forEach(equipmentInfos ->
                        equipmentInfos.setVariantId(variantId));
                tombstonedEquipmentInfos.forEach(equipmentInfos ->
                        equipmentInfos.setVariantId(variantId));

                // save all to ElasticSearch
                equipmentInfosService.addAll(createdEquipmentInfos);
                equipmentInfosService.addAll(modifiedEquipmentInfos);
                equipmentInfosService.addAllTombstonedEquipmentInfos(tombstonedEquipmentInfos);
            }
        } finally {
            LOGGER.trace("Indexation network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    public void deleteAllEquipmentInfosByNetworkUuid(UUID networkUuid) {
        equipmentInfosService.deleteAllByNetworkUuid(networkUuid);
    }

    public List<EquipmentInfos> getAllEquipmentInfos(UUID networkUuid) {
        return equipmentInfosService.findAll(networkUuid);
    }

    public List<EquipmentInfos> getAllEquipmentInfosByNetworkUuidAndVariantId(UUID networkUuid, String variantId) {
        return equipmentInfosService.findAllByNetworkUuidAndVariantId(networkUuid, variantId);
    }

    public List<TombstonedEquipmentInfos> getAllTombstonedEquipmentInfosByNetworkUuidAndVariantId(UUID networkUuid, String variantId) {
        return equipmentInfosService.findAllTombstonedByNetworkUuidAndVariantId(networkUuid, variantId);
    }

    public boolean hasEquipmentInfos(UUID networkUuid) {
        return equipmentInfosService.count(networkUuid) > 0;
    }
}
