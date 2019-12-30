/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.commons.datasource.ReadOnlyMemDataSource;
import com.powsybl.network.conversion.server.dto.NetworkIds;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreClient;
import com.powsybl.network.store.client.NetworkStoreService;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Service
@ComponentScan(basePackageClasses = {NetworkStoreClient.class})
public class NetworkConversionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionService.class);
    private String caseServerBaseUri;
    private String geoDataServerBaseUri;
    private RestTemplate caseServerRest;
    private RestTemplate geoDataServerRest;
    private static final  String IIDM_GEO_DATA_API_VERSION = "v1";

    @Autowired
    private NetworkStoreService networkStoreService;

    @Autowired
    public NetworkConversionService(@Value("${backing-services.case-server.base-uri:http://case-server/}") String caseServerBaseUri,
                                    @Value("${backing-services.geo-data-server.base-uri:http://geo-data-server/}") String geoDataServerBaseUri) {
        RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
        caseServerRest = restTemplateBuilder.build();
        caseServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri));
        this.caseServerBaseUri = caseServerBaseUri;

        restTemplateBuilder = new RestTemplateBuilder();
        geoDataServerRest = restTemplateBuilder.build();
        geoDataServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(geoDataServerBaseUri));
        this.geoDataServerBaseUri = geoDataServerBaseUri;
    }

    NetworkIds persistentStore(String caseName) {
        byte[] networkByte = getCaseAsByte(caseName);
        String[] baseName = caseName.split("\\.");
        ReadOnlyMemDataSource readOnlyMemDataSource = new ReadOnlyMemDataSource(baseName[0]);
        readOnlyMemDataSource.putData(caseName, networkByte);
        Network network = networkStoreService.importNetwork(readOnlyMemDataSource);
        UUID networkUuid = networkStoreService.getNetworkUuid(network);
        return new NetworkIds(networkUuid, network.getId());
    }

    byte[] getCaseAsByte(String caseName) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put("caseName", caseName);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(caseServerBaseUri + "/" + NetworkConversionConstants.CASE_API_VERSION + "/cases/{caseName}")
                .uriVariables(urlParams);

        try {
            ResponseEntity<byte[]> responseEntity = caseServerRest.exchange(uriBuilder.toUriString(),
                    HttpMethod.GET,
                    requestEntity,
                    byte[].class);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            throw new NetworkConversionException("getCaseAsByte HttpStatusCodeException", e);
        }
    }

    Network getNetwork(UUID networdUuid) {
        return networkStoreService.getNetwork(networdUuid);
    }

    void setCaseServerRest(RestTemplate caseServerRest) {
        Validate.notNull(caseServerRest, "caseServerRest can't be null");
        this.caseServerRest = caseServerRest;
    }

    void setGeoDataServerRest(RestTemplate geoDataServerRest) {
        Validate.notNull(geoDataServerRest, "geoDataServerRest can't be null");
        this.geoDataServerRest = geoDataServerRest;
    }
}
