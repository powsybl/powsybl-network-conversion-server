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
import com.powsybl.cgmes.extensions.CgmesSshMetadata;
import com.powsybl.cgmes.extensions.CgmesSvMetadata;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.ReporterModelDeserializer;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.iidm.export.Exporters;
import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.conversion.server.dto.BoundaryInfos;
import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import com.powsybl.network.conversion.server.dto.NetworkInfos;
import com.powsybl.network.conversion.server.elasticsearch.EquipmentInfosService;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.powsybl.network.conversion.server.NetworkConversionConstants.DELIMITER;
import static com.powsybl.network.conversion.server.NetworkConversionConstants.REPORT_API_VERSION;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@Service
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
public class NetworkConversionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionService.class);

    private RestTemplate caseServerRest;

    private RestTemplate geoDataServerRest;

    private RestTemplate reportServerRest;

    private final NetworkStoreService networkStoreService;

    private final EquipmentInfosService equipmentInfosService;

    private final NetworkConversionExecutionService networkConversionExecutionService;

    private final ObjectMapper objectMapper;

    @Autowired
    public NetworkConversionService(@Value("${backing-services.case-server.base-uri:http://case-server/}") String caseServerBaseUri,
                                    @Value("${backing-services.geo-data-server.base-uri:http://geo-data-server/}") String geoDataServerBaseUri,
                                    @Value("${backing-services.report-server.base-uri:http://report-server}") String reportServerURI,
                                    NetworkStoreService networkStoreService, @Lazy EquipmentInfosService equipmentInfosService, NetworkConversionExecutionService networkConversionExecutionService) {
        this.networkStoreService = networkStoreService;
        this.equipmentInfosService = equipmentInfosService;
        this.networkConversionExecutionService = networkConversionExecutionService;

        RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
        caseServerRest = restTemplateBuilder.build();
        caseServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri));

        geoDataServerRest = restTemplateBuilder.build();
        geoDataServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(geoDataServerBaseUri));

        reportServerRest = restTemplateBuilder.build();
        reportServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(reportServerURI));

        objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.registerModule(new ReporterModelJsonModule());
        objectMapper.setInjectableValues(new InjectableValues.Std().addValue(ReporterModelDeserializer.DICTIONARY_VALUE_ID, null));
    }

    private static EquipmentInfos toEquipmentInfos(Identifiable<?> i, UUID networkUuid, String variantId) {
        return EquipmentInfos.builder()
            .networkUuid(networkUuid)
            .variantId(variantId)
            .id(i.getId())
            .name(i.getNameOrId())
            .type(i.getType().name())
            .voltageLevels(EquipmentInfos.getVoltageLevels(i))
            .build();
    }

    NetworkInfos importCase(UUID caseUuid, String variantId) {
        CaseDataSourceClient dataSource = new CaseDataSourceClient(caseServerRest, caseUuid);
        ReporterModel reporter = new ReporterModel("importNetwork", "import network");
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        Network network = networkStoreService.importNetwork(dataSource, reporter, false);
        UUID networkUuid = networkStoreService.getNetworkUuid(network);
        LOGGER.trace("Import network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        saveNetwork(network, networkUuid, variantId, reporter);
        return new NetworkInfos(networkUuid, network.getId());
    }

    private void saveNetwork(Network network, UUID networkUuid, String variantId, ReporterModel reporter) {
        CompletableFuture<Void> saveInParallel = CompletableFuture.allOf(
            networkConversionExecutionService.runAsync(() -> storeNetworkInitialVariants(network, networkUuid, variantId)),
            networkConversionExecutionService.runAsync(() -> sendReport(networkUuid, reporter)),
            networkConversionExecutionService.runAsync(() -> insertEquipmentIndexes(network, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID))
        );
        try {
            saveInParallel.get();
        } catch (InterruptedException | ExecutionException e) {
            undoSaveNetwork(networkUuid);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw NetworkConversionException.createFailedNetworkSaving(networkUuid, e);
        }
    }

    private void undoSaveNetwork(UUID networkUuid) {
        CompletableFuture<Void> deleteInParallel = CompletableFuture.allOf(
            networkConversionExecutionService.runAsync(() -> networkStoreService.deleteNetwork(networkUuid)),
            networkConversionExecutionService.runAsync(() -> deleteReport(networkUuid)),
            networkConversionExecutionService.runAsync(() -> equipmentInfosService.deleteAll(networkUuid))
        );
        try {
            deleteInParallel.get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Network getNetwork(UUID networkUuid) {
        try {
            return networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    private Network networksListToMergedNetwork(List<Network> networks) {
        if (networks.size() == 1) {
            return networks.get(0);
        } else {
            // creation of the merging view and merging the networks
            MergingView merginvView = MergingView.create("merged_network", "iidm");

            merginvView.merge(networks.toArray(new Network[networks.size()]));

            return merginvView;
        }
    }

    ExportNetworkInfos exportNetwork(UUID networkUuid, String variantId, List<UUID> otherNetworksUuid, String format) throws IOException {
        if (!Exporters.getFormats().contains(format)) {
            throw NetworkConversionException.createFormatUnsupported(format);
        }
        MemDataSource memDataSource = new MemDataSource();

        Network network = networksListToMergedNetwork(getNetworkAsList(networkUuid, otherNetworksUuid));
        if (variantId != null) {
            if (network.getVariantManager().getVariantIds().contains(variantId)) {
                network.getVariantManager().setWorkingVariant(variantId);
            } else {
                throw NetworkConversionException.createVariantIdUnknown(variantId);
            }
        }

        Exporters.export(format, network, null, memDataSource);

        Set<String> listNames = memDataSource.listNames(".*");
        String networkName;
        byte[] networkData;
        networkName = network.getNameOrId();
        networkName += "_" + (variantId == null ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId);
        if (listNames.size() == 1) {
            networkName += listNames.toArray()[0];
            networkData = memDataSource.getData(listNames.toArray()[0].toString());
        } else {
            networkName += ".zip";
            networkData = createZipFile(listNames.toArray(new String[0]), memDataSource).toByteArray();
        }
        return new ExportNetworkInfos(networkName, networkData);
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

    Collection<String> getAvailableFormat() {
        return Exporters.getFormats();
    }

    void setCaseServerRest(RestTemplate caseServerRest) {
        this.caseServerRest = Objects.requireNonNull(caseServerRest, "caseServerRest can't be null");
    }

    void setGeoDataServerRest(RestTemplate geoDataServerRest) {
        this.geoDataServerRest = Objects.requireNonNull(geoDataServerRest, "geoDataServerRest can't be null");
    }

    public List<Network> getNetworkAsList(UUID networkUuid, List<UUID> otherNetworksUuid) {
        List<Network> networks = new ArrayList<>();
        networks.add(getNetwork(networkUuid));
        otherNetworksUuid.forEach(uuid -> networks.add(getNetwork(uuid)));
        return networks;
    }

    public ExportNetworkInfos exportCgmesSv(UUID networkUuid, List<UUID> otherNetworksUuid) throws XMLStreamException {
        List<Network> networks = getNetworkAsList(networkUuid, otherNetworksUuid);
        Network mergedNetwork = networksListToMergedNetwork(networks);

        Properties properties = new Properties();
        properties.put("iidm.import.cgmes.profile-used-for-initial-state-values", "SV");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLStreamWriter writer = null;

        try {
            writer = XmlUtil.initializeWriter(true, "    ", outputStream);
            StateVariablesExport.write(mergedNetwork, writer, createContext(mergedNetwork, networks));
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        return new ExportNetworkInfos(mergedNetwork.getNameOrId(), outputStream.toByteArray());
    }

    private static CgmesExportContext createContext(Network mergedNetwork, List<Network> networks) {
        CgmesExportContext context = new CgmesExportContext();
        context.setScenarioTime(mergedNetwork.getCaseDate());
        networks.forEach(network -> {
            context.getSvModelDescription().addDependencies(network.getExtension(CgmesSvMetadata.class).getDependencies());
            context.getSshModelDescription().addDependencies(network.getExtension(CgmesSshMetadata.class).getDependencies());
            context.addIidmMappings(network);
        });
        return context;
    }

    NetworkInfos importCgmesCase(UUID caseUuid, List<BoundaryInfos> boundaries) {
        if (CollectionUtils.isEmpty(boundaries)) {  // no boundaries given, standard import
            return importCase(caseUuid, null);
        } else {  // import using the given boundaries
            CaseDataSourceClient dataSource = new CgmesCaseDataSourceClient(caseServerRest, caseUuid, boundaries);
            var network = networkStoreService.importNetwork(dataSource);
            var networkUuid = networkStoreService.getNetworkUuid(network);
            return new NetworkInfos(networkUuid, network.getId());
        }
    }

    private void storeNetworkInitialVariants(Network network, UUID networkUuid, String variantId) {
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        try {
            networkStoreService.flush(network);
            if (variantId != null) {
                // cloning network initial variant into variantId
                networkStoreService.cloneVariant(networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID, variantId, false);
            }
        } finally {
            LOGGER.trace("Flush network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private void insertEquipmentIndexes(Network network, UUID networkUuid, String variantId) {
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        try {
            equipmentInfosService.addAll(
                network.getIdentifiables()
                    .stream()
                    .map(c -> toEquipmentInfos(c, networkUuid, variantId))
                    .collect(Collectors.toList()));
        } finally {
            LOGGER.trace("Indexation network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private void sendReport(UUID networkUuid, ReporterModel reporter) {
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resourceUrl = DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER + networkUuid.toString();
        var uriBuilder = UriComponentsBuilder.fromPath(resourceUrl);
        try {
            reportServerRest.exchange(uriBuilder.toUriString(), HttpMethod.PUT, new HttpEntity<>(objectMapper.writeValueAsString(reporter), headers), ReporterModel.class);
        } catch (JsonProcessingException error) {
            throw new PowsyblException("error creating report", error);
        } finally {
            LOGGER.trace("Save reports for network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private void deleteReport(UUID networkUuid) {
        var resourceUrl = DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER + networkUuid.toString();
        reportServerRest.delete(resourceUrl);
    }

    public void setReportServerRest(RestTemplate reportServerRest) {
        this.reportServerRest = Objects.requireNonNull(reportServerRest, "caseServerRest can't be null");
    }

    public void reindexAllEquipments(UUID networkUuid) {
        Network network = getNetwork(networkUuid);

        // delete all network equipments infos
        deleteAllEquipmentInfos(networkUuid);

        // recreate all equipments infos
        insertEquipmentIndexes(network, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
    }

    public void deleteAllEquipmentInfos(UUID networkUuid) {
        equipmentInfosService.deleteAll(networkUuid);
    }

    public List<EquipmentInfos> getAllEquipmentInfos(UUID networkUuid) {
        List<EquipmentInfos> infos = new ArrayList<>();
        equipmentInfosService.findAll(networkUuid).forEach(infos::add);
        return infos;
    }
}
