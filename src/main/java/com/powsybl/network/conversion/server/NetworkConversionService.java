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
import com.powsybl.commons.datasource.DataSourceUtil;
import com.powsybl.commons.datasource.DirectoryDataSource;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeDeserializer;
import com.powsybl.commons.report.ReportNodeJsonModule;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.network.conversion.server.dto.*;
import com.powsybl.network.conversion.server.elasticsearch.EquipmentInfosService;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
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

import static com.powsybl.commons.parameters.ParameterType.STRING_LIST;
import static com.powsybl.network.conversion.server.NetworkConversionConstants.DELIMITER;
import static com.powsybl.network.conversion.server.NetworkConversionConstants.REPORT_API_VERSION;
import static com.powsybl.network.conversion.server.NetworkConversionException.createFailedNetworkReindex;
import static com.powsybl.network.conversion.server.dto.EquipmentInfos.getEquipmentTypeName;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@Service
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
public class NetworkConversionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionService.class);

    public static final String METADATA_FILE_NAME = "file-name";

    public static final Set<IdentifiableType> TYPES_FOR_INDEXING = Set.of(
            IdentifiableType.SUBSTATION,
            IdentifiableType.VOLTAGE_LEVEL,
            IdentifiableType.HVDC_LINE,
            IdentifiableType.LINE,
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

    private final S3Client s3Client;

    private final String bucketName;

    private final String exportRootPath;

    public NetworkConversionService(@Value("${powsybl.services.case-server.base-uri:http://case-server/}") String caseServerBaseUri,
                                    @Value("${gridsuite.services.geo-data-server.base-uri:http://geo-data-server/}") String geoDataServerBaseUri,
                                    @Value("${gridsuite.services.report-server.base-uri:http://report-server}") String reportServerURI,
                                    NetworkStoreService networkStoreService,
                                    EquipmentInfosService equipmentInfosService,
                                    NetworkConversionExecutionService networkConversionExecutionService,
                                    NotificationService notificationService,
                                    NetworkConversionObserver networkConversionObserver,
                                    ImportExportExecutionService importExportExecutionService,
                                    S3Client s3Client,
                                    @Value("${spring.cloud.aws.bucket:ws-bucket}") String bucketName,
                                    @Value("${powsybl-ws.s3.subpath.prefix:}${export-subpath}") String exportRootPath) {
        this.networkStoreService = networkStoreService;
        this.equipmentInfosService = equipmentInfosService;
        this.networkConversionExecutionService = networkConversionExecutionService;
        this.notificationService = notificationService;
        this.networkConversionObserver = networkConversionObserver;
        this.importExportExecutionService = importExportExecutionService;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.exportRootPath = exportRootPath;

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

    void exportNetworkAsynchronously(UUID networkUuid, String variantId, String fileName, String format, String receiver, UUID exportUuid, Map<String, Object> formatParameters) {
        notificationService.emitNetworkExportStart(networkUuid, variantId, fileName, format, receiver, exportUuid, formatParameters);
    }

    void exportCaseAsynchronously(UUID caseUuid, String fileName, String format, String userId, UUID exportUuid, Map<String, Object> formatParameters) {
        notificationService.emitCaseExportStart(caseUuid, fileName, format, userId, exportUuid, formatParameters);
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

            NetworkInfos networkInfos = importCase(caseUuid, variantId, reportUuid, caseInfos.getFormat(), changedImportParameters);
            notificationService.emitCaseImportSucceeded(networkInfos, caseInfos.getName(), caseInfos.getFormat(), receiver, allImportParameters);
        };
    }

    @Bean
    Consumer<Message<UUID>> consumeNetworkExportStart() {
        return message -> {
            UUID networkUuid = message.getPayload();
            String variantId = message.getHeaders().get(NotificationService.HEADER_VARIANT_ID, String.class);
            String fileName = message.getHeaders().get(NotificationService.HEADER_FILE_NAME, String.class);
            String format = message.getHeaders().get(NotificationService.HEADER_FORMAT, String.class);
            String receiver = message.getHeaders().get(NotificationService.HEADER_RECEIVER, String.class);
            String exportUuidStr = message.getHeaders().get(NotificationService.HEADER_EXPORT_UUID, String.class);
            UUID exportUuid = exportUuidStr != null ? UUID.fromString(exportUuidStr) : null;
            Map<String, Object> formatParameters = extractFormatParameters(message);
            ExportNetworkInfos exportNetworkInfos = null;
            try {
                LOGGER.debug("Processing export for network {} with format {}...", networkUuid, format);
                exportNetworkInfos = networkConversionObserver.observeExportProcessing(
                        format,
                        () -> exportNetwork(networkUuid, variantId, fileName, format, formatParameters)
                );
                String s3Key = exportRootPath + "/" + exportUuid;
                uploadFile(exportNetworkInfos.getTempFilePath(), s3Key, exportNetworkInfos.getNetworkName());
                notificationService.emitNetworkExportFinished(exportUuid, receiver, null);
            } catch (Exception e) {
                String errorMsg = String.format("Export failed for network %s: %s", networkUuid, e.getMessage());
                notificationService.emitNetworkExportFinished(exportUuid, receiver, errorMsg);
                LOGGER.error(errorMsg);
            } finally {
                if (exportNetworkInfos != null) {
                    cleanUpTempFiles(exportNetworkInfos.getTempFilePath());
                }
            }
        };
    }

    public void uploadFile(Path filePath, String s3Key, String fileName) throws IOException {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .metadata(Map.of(METADATA_FILE_NAME, fileName))
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromFile(filePath));
        } catch (SdkException e) {
            throw new IOException("Error occurred while uploading file to S3: " + e.getMessage());
        }
    }

    public ResponseEntity<InputStreamResource> downloadExportFile(String exportUuid) {
        try {
            String s3Key = exportRootPath + "/" + exportUuid;
            GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(s3Key).build();
            ResponseInputStream<GetObjectResponse> s3InputStream = s3Client.getObject(getRequest);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.builder("attachment").filename(s3InputStream.response().metadata().get(METADATA_FILE_NAME)).build());
            headers.setContentLength(s3InputStream.response().contentLength());
            return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_OCTET_STREAM).body(new InputStreamResource(s3InputStream));
        } catch (NoSuchKeyException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Bean
    Consumer<Message<UUID>> consumeCaseExportStart() {
        return message -> {
            UUID caseUuid = message.getPayload();
            String format = message.getHeaders().get(NotificationService.HEADER_FORMAT, String.class);
            String fileName = message.getHeaders().get(NotificationService.HEADER_FILE_NAME, String.class);
            String userId = message.getHeaders().get(NotificationService.HEADER_USER_ID, String.class);
            String exportUuidStr = message.getHeaders().get(NotificationService.HEADER_EXPORT_UUID, String.class);
            UUID exportUuid = exportUuidStr != null ? UUID.fromString(exportUuidStr) : null;
            Map<String, Object> formatParameters = extractFormatParameters(message);
            ExportNetworkInfos exportNetworkInfos = null;
            try {
                LOGGER.debug("Processing export for case {} with format {}...", caseUuid, format);
                exportNetworkInfos = networkConversionObserver.observeExportProcessing(
                        format,
                        () -> exportCase(caseUuid, format, fileName, formatParameters)
                );
                String s3Key = exportRootPath + "/" + exportUuid;
                uploadFile(exportNetworkInfos.getTempFilePath(), s3Key, exportNetworkInfos.getNetworkName());
                notificationService.emitCaseExportFinished(exportUuid, userId, null);
            } catch (Exception e) {
                String errorMsg = String.format("Export failed for case %s: %s", caseUuid, e.getMessage());
                LOGGER.error(errorMsg);
                notificationService.emitCaseExportFinished(exportUuid, userId, errorMsg);
            } finally {
                if (exportNetworkInfos != null) {
                    cleanUpTempFiles(exportNetworkInfos.getTempFilePath());
                }
            }
        };
    }

    private Map<String, Object> extractFormatParameters(Message<UUID> message) {
        // String longer than 1024 bytes are converted to com.rabbitmq.client.LongString (https://docs.spring.io/spring-amqp/docs/3.0.0/reference/html/#message-properties-converters)
        Map<String, Object> rawParameters = (Map<String, Object>) message.getHeaders().get(NotificationService.HEADER_EXPORT_PARAMETERS);
        Map<String, Object> formatParameters = new HashMap<>();
        if (rawParameters != null) {
            rawParameters.forEach((key, value) -> {
                if (value != null) {
                    formatParameters.put(key, value.toString());
                }
            });
        }
        return formatParameters;
    }

    private NetworkInfos importCaseExec(UUID caseUuid, String variantId, UUID reportUuid, String caseFormat, Map<String, Object> importParameters) {
        CaseDataSourceClient dataSource = new CaseDataSourceClient(caseServerRest, caseUuid);
        ReportNode rootReport = ReportNode.NO_OP;
        ReportNode reporter = ReportNode.NO_OP;
        if (reportUuid != null) {
            String reporterId = "Root";
            rootReport = ReportNode.newRootReportNode()
                    .withAllResourceBundlesFromClasspath()
                    .withMessageTemplate("network.conversion.server.reporterId")
                    .withUntypedValue("reporterId", reporterId)
                    .build();

            String subReporterId = "Import Case : " + dataSource.getBaseName();
            reporter = rootReport.newReportNode()
                    .withMessageTemplate("network.conversion.server.subReporterId")
                    .withUntypedValue("subReporterId", subReporterId)
                    .add();
        }

        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        ReportNode finalReporter = reporter;
        Network network = networkConversionObserver.observeImportProcessing(caseFormat, () -> {
            if (!importParameters.isEmpty()) {
                Properties importProperties = new Properties();
                importProperties.putAll(importParameters);
                return networkStoreService.importNetwork(dataSource, finalReporter, importProperties, false);
            } else {
                return networkStoreService.importNetwork(dataSource, finalReporter, false);
            }
        });
        UUID networkUuid = networkStoreService.getNetworkUuid(network);
        LOGGER.trace("Import network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        saveNetwork(network, networkUuid, variantId, rootReport, reportUuid);
        return new NetworkInfos(networkUuid, network.getId());
    }

    public NetworkInfos importCase(UUID caseUuid, String variantId, UUID reportUuid, String caseFormat, Map<String, Object> importParameters) {
        try {
            return networkConversionObserver.observeImportTotal(caseFormat, () ->
                    importExportExecutionService.supplyAsync(() ->
                            importCaseExec(caseUuid, variantId, reportUuid, caseFormat, importParameters)
                    ).join()
            );
        } catch (CompletionException e) {
            throw NetworkConversionException.createFailedCaseImport(e.getCause());
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
        String format, Map<String, Object> formatParameters) {
        Properties exportProperties = initializePropertiesAndCheckFormat(format, formatParameters);
        Network network = getNetwork(networkUuid);
        if (variantId != null) {
            if (network.getVariantManager().getVariantIds().contains(variantId)) {
                network.getVariantManager().setWorkingVariant(variantId);
            } else {
                throw NetworkConversionException.createVariantIdUnknown(variantId);
            }
        }
        String fileOrNetworkName = fileName != null ? fileName : getNetworkName(network, variantId);
        long networkSize = network.getBusView().getBusStream().count();
        return getExportNetworkInfos(network, format, fileOrNetworkName, exportProperties, networkSize, false);
    }

    public ExportNetworkInfos exportNetwork(UUID networkUuid, String variantId, String fileName,
        String format, Map<String, Object> formatParameters) {
        try {
            return networkConversionObserver.observeExportTotal(format, () ->
                    importExportExecutionService.supplyAsync(() ->
                        networkConversionObserver.observeExportProcessing(
                            format,
                            () -> exportNetworkExec(networkUuid, variantId, fileName, format, formatParameters)))
                        .join()
            );
        } catch (CompletionException e) {
            if (e.getCause() instanceof NetworkConversionException exception) {
                throw exception;
            }
            throw NetworkConversionException.createFailedCaseExport(e);
        }
    }

    public ExportNetworkInfos exportCase(UUID caseUuid, String format, String fileName, Map<String, Object> formatParameters) {
        try {
            return networkConversionObserver.observeExportTotal(format, () ->
                importExportExecutionService.supplyAsync(() ->
                    networkConversionObserver.observeExportProcessing(format, () -> exportCaseExec(caseUuid, format, fileName, formatParameters)))
                    .join());
        } catch (CompletionException e) {
            if (e.getCause() instanceof NetworkConversionException exception) {
                throw exception;
            }
            throw NetworkConversionException.createFailedCaseExport(e);
        }
    }

    public ExportNetworkInfos exportCaseExec(UUID caseUuid, String format, String fileName, Map<String, Object> formatParameters) {
        Properties exportProperties = initializePropertiesAndCheckFormat(format, formatParameters);
        CaseDataSourceClient dataSource = new CaseDataSourceClient(caseServerRest, caseUuid);

        // build import properties to import all available extensions
        // TODO : Check at next powsybl upgrade if this code is still required. To be removed if not useful anymore
        Properties importProperties = new Properties();
        ImportExportFormatMeta caseImportParameters = getCaseImportParameters(caseUuid);
        Optional<ParamMeta> paramExtensions = caseImportParameters.getParameters().stream().filter(param -> param.getName().endsWith("extensions") && param.getType() == STRING_LIST).findFirst();
        paramExtensions.ifPresent(paramMeta -> importProperties.put(paramMeta.getName(), paramMeta.getPossibleValues()));

        Network network = Network.read(dataSource, LocalComputationManager.getDefault(), ImportConfig.load(),
                importProperties, NetworkFactory.find("NetworkStore"), new ImportersServiceLoader(), ReportNode.NO_OP);
        String fileOrNetworkName = fileName != null ? fileName : DataSourceUtil.getBaseName(dataSource.getBaseName());
        long networkSize = network.getBusView().getBusStream().count();
        return getExportNetworkInfos(network, format, fileOrNetworkName, exportProperties, networkSize, true);
    }

    private String getNetworkName(Network network, String variantId) {
        String networkName = network.getNameOrId();
        networkName += "_" + (variantId == null ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId);
        return networkName;
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
        return networkConversionObserver.observeExportTotal("CGMES", () -> exportCgmesSvExec(networkUuid));
    }

    public ExportNetworkInfos exportCgmesSvExec(UUID networkUuid) throws XMLStreamException {
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
            Network network = networkConversionObserver.observeImportTotal(caseFormat, () -> networkStoreService.importNetwork(dataSource));
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
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        try {
            Network initialNetwork = getNetwork(networkUuid);

            // delete all network equipments infos. deleting a lot of documents in ElasticSearch is slow, we delete the index instead in maintenance script before reindexing
            deleteAllEquipmentInfosByNetworkUuid(networkUuid);

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
                // switch to working variant and change initial variant infos variantId for comparisons
                // get a new network (and associated cache) to avoid loading all variants in the same cache
                Network currentNetwork = getNetwork(networkUuid);
                currentNetwork.getVariantManager().setWorkingVariant(variantId);
                initialVariantEquipmentInfos.values().forEach(equipmentInfos ->
                        equipmentInfos.setVariantId(variantId));

                // get current variant infos
                Map<String, EquipmentInfos> currentVariantEquipmentInfos = getEquipmentInfos(currentNetwork, networkUuid, variantId);

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

                // save all to ElasticSearch
                equipmentInfosService.addAll(createdEquipmentInfos);
                equipmentInfosService.addAll(modifiedEquipmentInfos);
                equipmentInfosService.addAllTombstonedEquipmentInfos(tombstonedEquipmentInfos);
            }
        } catch (Exception e) {
            throw createFailedNetworkReindex(networkUuid, e);
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

    private Properties initializePropertiesAndCheckFormat(String format, Map<String, Object> formatParameters) {
        if (!Exporter.getFormats().contains(format)) {
            throw NetworkConversionException.createUnsupportedFormat(format);
        }
        Properties exportProperties = null;
        if (formatParameters != null) {
            exportProperties = new Properties();
            exportProperties.putAll(formatParameters);
        }
        return exportProperties;
    }

    private ExportNetworkInfos getExportNetworkInfos(Network network, String format,
                                                     String fileOrNetworkName, Properties exportProperties,
                                                     long networkSize, boolean withNotZipFileName) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("export_", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            String finalFileOrNetworkName = fileOrNetworkName.replace('/', '_');
            DirectoryDataSource dataSource = new DirectoryDataSource(tempDir, finalFileOrNetworkName);
            network.write(format, exportProperties, dataSource);

            Set<String> fileNames = dataSource.listNames(".*");
            if (fileNames.isEmpty()) {
                throw new IOException("No files were created during export");
            }

            Path filePath;
            if (fileNames.size() == 1 && withNotZipFileName) {
                filePath = tempDir.resolve(fileNames.iterator().next());
            } else {
                filePath = createZipFile(tempDir, fileOrNetworkName, fileNames);
            }
            return new ExportNetworkInfos(filePath.getFileName().toString(), filePath, networkSize);
        } catch (IOException e) {
            if (tempDir != null) {
                cleanUpTempFiles(tempDir);
            }
            throw NetworkConversionException.failedToStreamNetworkToFile(e);
        }
    }

    private Path createZipFile(Path tempDir, String fileOrNetworkName, Set<String> fileNames) throws IOException {
        Path zipFile = tempDir.resolve(fileOrNetworkName + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (String fileName : fileNames) {
                Path sourceFile = tempDir.resolve(fileName);
                zos.putNextEntry(new ZipEntry(fileName));
                try (InputStream is = Files.newInputStream(sourceFile)) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        return zipFile;
    }

    public void cleanUpTempFiles(Path tempFilePath) {
        try {
            if (Files.exists(tempFilePath)) {
                FileUtils.deleteDirectory(tempFilePath.getParent().toFile());
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
