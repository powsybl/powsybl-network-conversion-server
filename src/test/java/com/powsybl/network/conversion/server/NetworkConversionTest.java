/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@RunWith(SpringRunner.class)
@WebMvcTest(NetworkConversionController.class)
@ContextConfiguration(classes = {NetworkConversionApplication.class, NetworkConversionService.class})
public class NetworkConversionTest {

    @Autowired
    private MockMvc mvc;

    @Mock
    private RestTemplate caseServerRest;

    @Mock
    private RestTemplate geoDataRest;

    @Autowired
    private NetworkConversionService networkConversionService;

    @MockBean
    private NetworkStoreService networkStoreClient;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/testCase.xiidm")) {
            byte[] networkByte = IOUtils.toByteArray(inputStream);
            networkConversionService.setCaseServerRest(caseServerRest);
            networkConversionService.setGeoDataServerRest(geoDataRest);
            given(caseServerRest.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                    .willReturn(new ResponseEntity<>(networkByte, HttpStatus.OK));

            ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                    new ResourceSet("", "testCase.xiidm"));
            Network network = Importers.importData("XIIDM", dataSource, null);

            given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class))).willReturn(network);
            UUID randomUuid = UUID.fromString("78e13f90-f351-4c2e-a383-2ad08dd5f8fb");
            given(networkStoreClient.getNetworkUuid(network)).willReturn(randomUuid);

            MvcResult mvcResult = mvc.perform(post("/v1/networks").param("caseUuid", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertEquals("{\"networkUuid\":\"78e13f90-f351-4c2e-a383-2ad08dd5f8fb\",\"networkId\":\"20140116_0830_2D4_UX1_pst\"}",
                    mvcResult.getResponse().getContentAsString());

            mvc.perform(get("/v1/export/formats"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn();

            given(networkStoreClient.getNetwork(any(UUID.class))).willReturn(network);
            mvcResult = mvc.perform(get("/v1/networks/{networkUuid}/{format}", UUID.randomUUID().toString(), "XIIDM"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertTrue(mvcResult.getResponse().getContentAsString().startsWith("{\"networkName\":\"20140116_0830_2D4_UX1_pst.xiidm"));
        }
    }
}
