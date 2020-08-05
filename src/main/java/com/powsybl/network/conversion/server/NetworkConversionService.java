/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.cases.datasource.CaseDataSourceClient;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.export.Exporters;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import com.powsybl.network.conversion.server.dto.NetworkInfos;
import com.powsybl.network.store.client.NetworkStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
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

    ExportNetworkInfos exportNetwork(UUID networkUuid, String format) throws IOException {
        if (!Exporters.getFormats().contains(format)) {
            throw NetworkConversionException.createFormatUnsupported(format);
        }
        MemDataSource memDataSource = new MemDataSource();
        Network network = networkStoreService.getNetwork(networkUuid);
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
}
