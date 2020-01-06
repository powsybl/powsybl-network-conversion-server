/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.google.common.collect.ImmutableMap;
import com.powsybl.commons.datasource.ReadOnlyMemDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.NetworkInfos;
import com.powsybl.network.store.client.NetworkStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Service
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
public class NetworkConversionService {

    private String caseServerBaseUri;
    private RestTemplate caseServerRest;
    private RestTemplate geoDataServerRest;

    @Autowired
    private NetworkStoreService networkStoreService;

    @Autowired
    public NetworkConversionService(@Value("${backing-services.case-server.base-uri:http://case-server/}") String caseServerBaseUri,
                                    @Value("${backing-services.geo-data-server.base-uri:http://geo-data-server/}") String geoDataServerBaseUri) {
        caseServerRest = new RestTemplateBuilder().build();
        caseServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri));
        this.caseServerBaseUri = caseServerBaseUri;

        geoDataServerRest = new RestTemplateBuilder().build();
        geoDataServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(geoDataServerBaseUri));
    }

    NetworkInfos storeCase(String caseName) {
        byte[] networkByte = getCaseAsByte(caseName);
        String[] baseName = caseName.split("\\.");
        ReadOnlyMemDataSource readOnlyMemDataSource = new ReadOnlyMemDataSource(baseName[0]);
        readOnlyMemDataSource.putData(caseName, networkByte);
        Network network = networkStoreService.importNetwork(readOnlyMemDataSource);
        UUID networkUuid = networkStoreService.getNetworkUuid(network);
        return new NetworkInfos(networkUuid, network.getId());
    }

    byte[] getCaseAsByte(String caseName) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity<byte[]> requestEntity = new HttpEntity<>(requestHeaders);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(caseServerBaseUri + "/" + NetworkConversionConstants.CASE_API_VERSION + "/cases/{caseName}")
                .uriVariables(ImmutableMap.of("caseName", caseName));

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

    void setCaseServerRest(RestTemplate caseServerRest) {
        this.caseServerRest = Objects.requireNonNull(caseServerRest, "caseServerRest can't be null");
    }

    void setGeoDataServerRest(RestTemplate geoDataServerRest) {
        this.geoDataServerRest = Objects.requireNonNull(geoDataServerRest, "geoDataServerRest can't be null");
    }
}
