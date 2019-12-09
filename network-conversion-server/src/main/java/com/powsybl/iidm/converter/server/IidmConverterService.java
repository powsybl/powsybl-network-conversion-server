/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.converter.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.ReadOnlyMemDataSource;
import com.powsybl.iidm.converter.server.dto.NetworkIds;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreClient;
import com.powsybl.network.store.client.NetworkStoreService;
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

import static com.powsybl.iidm.converter.server.IidmConverterConstants.CASE_API_VERSION;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Service
@ComponentScan(basePackageClasses = {NetworkStoreClient.class})
public class IidmConverterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IidmConverterService.class);
    private String caseServerBaseUri;
    private String geoDataBaseUri;
    private RestTemplate caseServerRest;
    private RestTemplate geoDataRest;
    private static final  String IIDM_GEO_DATA_API_VERSION = "v1";

    @Autowired
    private NetworkStoreService networkStoreService;

    @Autowired
    public IidmConverterService(@Value("${backing-services.case.base-uri:http://case-server/}") String caseServerBaseUri,
                                @Value("${backing-services.geo-data.base-uri:http://geo-data-server/}") String geoDataBaseUri) {
        RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
        caseServerRest = restTemplateBuilder.build();
        caseServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri));
        this.caseServerBaseUri = caseServerBaseUri;

        restTemplateBuilder = new RestTemplateBuilder();
        geoDataRest = restTemplateBuilder.build();
        geoDataRest.setUriTemplateHandler(new DefaultUriBuilderFactory(geoDataBaseUri));
        this.geoDataBaseUri = geoDataBaseUri;
    }

    NetworkIds persistentStore(String caseName) {
        byte[] networkByte = getCaseAsByte(caseName);
        String[] baseName = caseName.split("\\.");
        ReadOnlyMemDataSource readOnlyMemDataSource = new ReadOnlyMemDataSource(baseName[0]);
        readOnlyMemDataSource.putData(caseName, networkByte);
        Network network = networkStoreService.importNetwork(readOnlyMemDataSource);
        UUID networkUuid = networkStoreService.getNetworkUuid(network);
        precalculateLines(networkUuid);
        return new NetworkIds(networkUuid, network.getId());
    }

    private void precalculateLines(UUID idNetwork) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put("idNetwork", idNetwork);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(geoDataBaseUri + "/" + IIDM_GEO_DATA_API_VERSION +
                "/precalculate-lines/{idNetwork}").uriVariables(urlParams);

        geoDataRest.exchange(uriBuilder.toUriString(),
               HttpMethod.GET,
               requestEntity,
               String.class);
    }

    byte[] getCaseAsByte(String caseName) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put("caseName", caseName);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(caseServerBaseUri + "/" + CASE_API_VERSION + "/case-server/cases/{caseName}")
                .uriVariables(urlParams);

        try {
            ResponseEntity<byte[]> responseEntity = caseServerRest.exchange(uriBuilder.toUriString(),
                    HttpMethod.GET,
                    requestEntity,
                    byte[].class);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            LOGGER.error("getCaseAsByte HttpStatusCodeException :", e);
            HttpStatus exceptionCode = e.getStatusCode();
            if (exceptionCode == HttpStatus.CONFLICT || exceptionCode == HttpStatus.INTERNAL_SERVER_ERROR
                || exceptionCode == HttpStatus.UNPROCESSABLE_ENTITY || exceptionCode == HttpStatus.NO_CONTENT) {
                throw new PowsyblException(e.getMessage());
            } else {
                throw e;
            }
        }
    }

    Network getNetwork(UUID networdUuid) {
        return networkStoreService.getNetwork(networdUuid);
    }

    void setCaseServerRest(RestTemplate caseServerRest) {
        this.caseServerRest = caseServerRest;
    }

    void setGeoDataRest(RestTemplate geoDataRest) {
        this.geoDataRest = geoDataRest;
    }
}
