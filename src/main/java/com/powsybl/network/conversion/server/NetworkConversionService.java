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
import com.powsybl.network.conversion.server.dto.*;
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
import java.util.concurrent.Executors;
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

    private NetworkStoreService networkStoreService;

    private EquipmentInfosService equipmentInfosService;

    private ObjectMapper objectMapper;

    @Autowired
    public NetworkConversionService(@Value("${backing-services.case-server.base-uri:http://case-server/}") String caseServerBaseUri,
                                    @Value("${backing-services.geo-data-server.base-uri:http://geo-data-server/}") String geoDataServerBaseUri,
                                    @Value("${backing-services.report-server.base-uri:http://report-server}") String reportServerURI,
                                    NetworkStoreService networkStoreService, EquipmentInfosService equipmentInfosService) {
        this.networkStoreService = networkStoreService;
        this.equipmentInfosService = equipmentInfosService;

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

    private static EquipmentInfos toEquipmentInfos(Identifiable<?> i, UUID networkUuid) {
        return EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .equipmentId(i.getId())
                .equipmentName(i.getNameOrId())
                .equipmentType(EquipmentType.getType(i).name())
                .build();
    }

    NetworkInfos importCase(UUID caseUuid) {
        CaseDataSourceClient dataSource = new CaseDataSourceClient(caseServerRest, caseUuid);
        ReporterModel reporter = new ReporterModel("importNetwork", "import network");
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        Network network = networkStoreService.importNetwork(dataSource, reporter, false);
        UUID networkUuid = networkStoreService.getNetworkUuid(network);
        LOGGER.info("Import network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        saveNetwork(network, networkUuid, reporter);
        return new NetworkInfos(networkUuid, network.getId());
    }

    private void saveNetwork(Network network, UUID networkUuid, ReporterModel reporter) {
        AtomicReference<Boolean> hasFailed = new AtomicReference<>(false);
        AtomicReference<Exception> error = new AtomicReference<>();
        Arrays.<Runnable>asList(
            () -> flushNetwork(network, networkUuid),
            () -> sendReport(networkUuid, reporter),
            () -> insertEquipmentIndexes(network, networkUuid)
            )
            .parallelStream()
            .map(r -> Executors.newCachedThreadPool().submit(r))
            .forEach(f -> {
                try {
                    f.get(); // wait the end of each DB insert
                } catch (Exception e) {
                    hasFailed.set(true);
                    error.set(e);
                    Thread.currentThread().interrupt();
                }
            });
        if (hasFailed.get().booleanValue()) {
            undoSaveNetwork(networkUuid);
            throw NetworkConversionException.createFailedNetworkSaving(networkUuid, error.get());
        }
    }

    private void undoSaveNetwork(UUID networkUuid) {
        Arrays.<Runnable>asList(
            () -> networkStoreService.deleteNetwork(networkUuid),
            () -> deleteReport(networkUuid),
            () -> equipmentInfosService.deleteAll(networkUuid)
            )
            .parallelStream()
            .map(r -> Executors.newCachedThreadPool().submit(r))
            .forEach(f -> {
                try {
                    f.get();
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                }
            });
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

    ExportNetworkInfos exportNetwork(UUID networkUuid, List<UUID> otherNetworksUuid, String format) throws IOException {
        if (!Exporters.getFormats().contains(format)) {
            throw NetworkConversionException.createFormatUnsupported(format);
        }
        MemDataSource memDataSource = new MemDataSource();

        Network network = networksListToMergedNetwork(getNetworkAsList(networkUuid, otherNetworksUuid));

        Exporters.export(format, network, null, memDataSource);

        Set<String> listNames = memDataSource.listNames(".*");
        String networkName;
        byte[] networkData;
        if (listNames.size() == 1) {
            networkName = network.getNameOrId() + listNames.toArray()[0];
            networkData = memDataSource.getData(listNames.toArray()[0].toString());
        } else {
            networkName = network.getNameOrId() + ".zip";
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
            context.addTopologicalNodeMappings(network);
        });
        return context;
    }

    NetworkInfos importCgmesCase(UUID caseUuid, List<BoundaryInfos> boundaries) {
        if (CollectionUtils.isEmpty(boundaries)) {  // no boundaries given, standard import
            return importCase(caseUuid);
        } else {  // import using the given boundaries
            CaseDataSourceClient dataSource = new CgmesCaseDataSourceClient(caseServerRest, caseUuid, boundaries);
            var network = networkStoreService.importNetwork(dataSource);
            var networkUuid = networkStoreService.getNetworkUuid(network);
            return new NetworkInfos(networkUuid, network.getId());
        }
    }

    private void flushNetwork(Network network, UUID networkUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        try {
            networkStoreService.flush(network);
        } finally {
            LOGGER.info("Flush network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private void insertEquipmentIndexes(Network network, UUID networkUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>(System.nanoTime());
        try {
            network.getIdentifiables()
                    .stream()
                    //.filter(Predicate.not(i -> i instanceof Switch))
                    .map(c -> toEquipmentInfos(c, networkUuid))
                    .collect(Collectors.groupingBy(EquipmentInfos::getEquipmentType)).values()
                    .parallelStream()
                    .forEach(equipmentInfosService::addAll);
        } finally {
            LOGGER.info("Indexation network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
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
            LOGGER.info("Save reports for network '{}' in parallel : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private void deleteReport(UUID networkUuid) {
        var resourceUrl = DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER + networkUuid.toString();
        reportServerRest.delete(resourceUrl);
    }

    public void setReportServerRest(RestTemplate reportServerRest) {
        this.reportServerRest = Objects.requireNonNull(reportServerRest, "caseServerRest can't be null");
    }
}
