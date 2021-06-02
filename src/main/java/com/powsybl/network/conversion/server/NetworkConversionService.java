/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.cases.datasource.CaseDataSourceClient;
import com.powsybl.cgmes.conversion.export.CgmesExportContext;
import com.powsybl.cgmes.conversion.export.StateVariablesExport;
import com.powsybl.cgmes.extensions.CgmesSvMetadata;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.iidm.export.Exporters;
import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.BoundaryInfos;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import com.powsybl.network.conversion.server.dto.NetworkInfos;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@Service
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
public class NetworkConversionService {

    private RestTemplate caseServerRest;

    private RestTemplate geoDataServerRest;

    @Autowired
    private NetworkStoreService networkStoreService;

    @Autowired
    public NetworkConversionService(@Value("${backing-services.case-server.base-uri:http://case-server/}") String caseServerBaseUri,
                                    @Value("${backing-services.geo-data-server.base-uri:http://geo-data-server/}") String geoDataServerBaseUri) {
        RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
        caseServerRest = restTemplateBuilder.build();
        caseServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri));

        geoDataServerRest = restTemplateBuilder.build();
        geoDataServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(geoDataServerBaseUri));
    }

    NetworkInfos importCase(UUID caseUuid) {
        CaseDataSourceClient dataSource = new CaseDataSourceClient(caseServerRest, caseUuid);
        Network network = networkStoreService.importNetwork(dataSource);
        UUID networkUuid = networkStoreService.getNetworkUuid(network);
        return new NetworkInfos(networkUuid, network.getId());
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
        } catch (Exception e) {
            throw new PowsyblException(e);
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
}
