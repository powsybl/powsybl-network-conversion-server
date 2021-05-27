/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.cgmes.conformity.test.CgmesConformity1Catalog;
import com.powsybl.cgmes.conversion.CgmesImport;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.*;
import com.powsybl.network.conversion.server.dto.BoundaryInfos;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
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

            assertEquals("{\"networkUuid\":\"" + randomUuid + "\",\"networkId\":\"20140116_0830_2D4_UX1_pst\"}",
                    mvcResult.getResponse().getContentAsString());

            mvc.perform(get("/v1/export/formats"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn();

            given(networkStoreClient.getNetwork(any(UUID.class), eq(PreloadingStrategy.COLLECTION))).willReturn(network);
            mvcResult = mvc.perform(get("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "XIIDM"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                    .andReturn();

            assertEquals("attachment; filename*=UTF-8''20140116_0830_2D4_UX1_pst.xiidm", mvcResult.getResponse().getHeader("content-disposition"));
            assertTrue(mvcResult.getResponse().getContentAsString().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        }
    }

    @Test
    public void testWithMergingView() throws Exception {
        UUID testNetworkId1 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID testNetworkId2 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        UUID testNetworkId3 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

        given(networkStoreClient.getNetwork(testNetworkId1, PreloadingStrategy.COLLECTION)).willReturn(createNetwork("1_"));
        given(networkStoreClient.getNetwork(testNetworkId2, PreloadingStrategy.COLLECTION)).willReturn(createNetwork("2_"));
        given(networkStoreClient.getNetwork(testNetworkId3, PreloadingStrategy.COLLECTION)).willReturn(createNetwork("3_"));

        MvcResult mvcResult = mvc.perform(get("/v1/networks/{networkUuid}/export/{format}", testNetworkId1.toString(), "XIIDM")
                                          .param("networkUuid", testNetworkId2.toString())
                                          .param("networkUuid", testNetworkId3.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();

        assertEquals("attachment; filename*=UTF-8''merged_network.xiidm", mvcResult.getResponse().getHeader("content-disposition"));
        assertTrue(mvcResult.getResponse().getContentAsString().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
    }

    @Test
    public void testExportSv() throws Exception {
        Network network = new CgmesImport()
                .importData(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource(), NetworkFactory.findDefault(), null);
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e7");
        given(networkStoreClient.getNetwork(networkUuid, PreloadingStrategy.COLLECTION)).willReturn(network);

        MvcResult mvcResult = mvc.perform(get("/v1/networks/{networkUuid}/export-sv-cgmes", networkUuid))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();

        assertEquals("attachment; filename*=UTF-8''urn%3Auuid%3Ad400c631-75a0-4c30-8aed-832b0d282e73", mvcResult.getResponse().getHeader("content-disposition"));
        assertTrue(mvcResult.getResponse().getContentAsString().contains("<md:Model.description>SV Model</md:Model.description>\n" +
                "        <md:Model.version>1</md:Model.version>\n" +
                "        <md:Model.DependentOn rdf:resource=\"urn:uuid:2399cbd1-9a39-11e0-aa80-0800200c9a66\"/>\n" +
                "        <md:Model.DependentOn rdf:resource=\"urn:uuid:d400c631-75a0-4c30-8aed-832b0d282e73\"/>\n" +
                "        <md:Model.DependentOn rdf:resource=\"urn:uuid:f2f43818-09c8-4252-9611-7af80c398d20\"/>\n" +
                "        <md:Model.profile>http://entsoe.eu/CIM/StateVariables/4/1</md:Model.profile>\n" +
                "        <md:Model.modelingAuthoritySet>powsybl.org</md:Model.modelingAuthoritySet>\n" +
                "    </md:FullModel>"));
    }

    @Test
    public void testCgmesCaseDataSource() throws IOException, URISyntaxException {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        List<BoundaryInfos> boundaries = new ArrayList<>();
        String eqbdContent = "fake content of eqbd boundary";
        String tpbdContent = "fake content of tpbd boundary";

        boundaries.add(new BoundaryInfos("urn:uuid:f1582c44-d9e2-4ea0-afdc-dba189ab4358", "20201121T0000Z__ENTSOE_EQBD_003.xml", eqbdContent));
        boundaries.add(new BoundaryInfos("urn:uuid:3e3f7738-aab9-4284-a965-71d5cd151f71", "20201205T1000Z__ENTSOE_TPBD_004.xml", tpbdContent));

        byte[] sshContent = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("20210326T0930Z_1D_BE_SSH_6.xml").toURI()));

        CgmesCaseDataSourceClient client = new CgmesCaseDataSourceClient(caseServerRest, caseUuid, boundaries);

        given(caseServerRest.exchange(eq("/v1/cases/" + caseUuid + "/datasource?fileName=20210326T0930Z_1D_BE_SSH_6.xml"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(byte[].class)))
            .willReturn(ResponseEntity.ok(sshContent));

        InputStream input = client.newInputStream("20210326T0930Z_1D_BE_SSH_6.xml");
        assertArrayEquals(sshContent, org.apache.commons.io.IOUtils.toByteArray(input));

        input = client.newInputStream("20210326T0000Z__ENTSOE_EQBD_101.xml");
        assertArrayEquals(eqbdContent.getBytes(StandardCharsets.UTF_8), org.apache.commons.io.IOUtils.toByteArray(input));

        input = client.newInputStream("20210326T0000Z__ENTSOE_TPBD_6.xml");
        assertArrayEquals(tpbdContent.getBytes(StandardCharsets.UTF_8), org.apache.commons.io.IOUtils.toByteArray(input));

        final CgmesCaseDataSourceClient client2 = new CgmesCaseDataSourceClient(caseServerRest, caseUuid, Collections.emptyList());
        assertThrows(PowsyblException.class, () -> client2.newInputStream("20210326T0000Z__ENTSOE_EQBD_101.xml"));
    }

    @Test
    public void testImportCgmesCase() throws Exception {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e7");

        List<BoundaryInfos> boundaries = new ArrayList<>();
        String eqbdContent = "fake content of eqbd boundary";
        String tpbdContent = "fake content of tpbd boundary";
        boundaries.add(new BoundaryInfos("urn:uuid:f1582c44-d9e2-4ea0-afdc-dba189ab4358", "20201121T0000Z__ENTSOE_EQBD_003.xml", eqbdContent));
        boundaries.add(new BoundaryInfos("urn:uuid:3e3f7738-aab9-4284-a965-71d5cd151f71", "20201205T1000Z__ENTSOE_TPBD_004.xml", tpbdContent));

        Network network = new CgmesImport().importData(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource(), NetworkFactory.findDefault(), null);
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class))).willReturn(network);
        given(networkStoreClient.getNetworkUuid(network)).willReturn(networkUuid);

        MvcResult mvcResult = mvc.perform(post("/v1/networks/cgmes")
            .param("caseUuid", caseUuid.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(boundaries)))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("{\"networkUuid\":\"" + networkUuid + "\",\"networkId\":\"urn:uuid:d400c631-75a0-4c30-8aed-832b0d282e73\"}",
            mvcResult.getResponse().getContentAsString());

        mvcResult = mvc.perform(post("/v1/networks/cgmes")
            .param("caseUuid", caseUuid.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(Collections.emptyList())))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("{\"networkUuid\":\"" + networkUuid + "\",\"networkId\":\"urn:uuid:d400c631-75a0-4c30-8aed-832b0d282e73\"}",
            mvcResult.getResponse().getContentAsString());
    }

    public Network createNetwork(String prefix) {
        Network network = NetworkFactory.findDefault().createNetwork(prefix + "network", "test");
        Substation p1 = network.newSubstation()
                .setId(prefix + "P1")
                .setCountry(Country.FR)
                .setTso("RTE")
                .setGeographicalTags("A")
                .add();
        Substation p2 = network.newSubstation()
                .setId(prefix + "P2")
                .setCountry(Country.FR)
                .setTso("RTE")
                .setGeographicalTags("B")
                .add();
        VoltageLevel vlgen = p1.newVoltageLevel()
                .setId(prefix + "VLGEN")
                .setNominalV(24.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vlhv1 = p1.newVoltageLevel()
                .setId(prefix + "VLHV1")
                .setNominalV(380.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vlhv2 = p2.newVoltageLevel()
                .setId(prefix + "VLHV2")
                .setNominalV(380.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vlload = p2.newVoltageLevel()
                .setId(prefix + "VLLOAD")
                .setNominalV(150.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus ngen = vlgen.getBusBreakerView().newBus()
                .setId(prefix + "NGEN")
                .add();
        Bus nhv1 = vlhv1.getBusBreakerView().newBus()
                .setId(prefix + "NHV1")
                .add();
        Bus nhv2 = vlhv2.getBusBreakerView().newBus()
                .setId(prefix + "NHV2")
                .add();
        Bus nload = vlload.getBusBreakerView().newBus()
                .setId(prefix + "NLOAD")
                .add();
        network.newLine()
                .setId(prefix + "NHV1_NHV2_1")
                .setVoltageLevel1(vlhv1.getId())
                .setBus1(nhv1.getId())
                .setConnectableBus1(nhv1.getId())
                .setVoltageLevel2(vlhv2.getId())
                .setBus2(nhv2.getId())
                .setConnectableBus2(nhv2.getId())
                .setR(3.0)
                .setX(33.0)
                .setG1(0.0)
                .setB1(386E-6 / 2)
                .setG2(0.0)
                .setB2(386E-6 / 2)
                .add();
        network.newLine()
                .setId(prefix + "NHV1_NHV2_2")
                .setVoltageLevel1(vlhv1.getId())
                .setBus1(nhv1.getId())
                .setConnectableBus1(nhv1.getId())
                .setVoltageLevel2(vlhv2.getId())
                .setBus2(nhv2.getId())
                .setConnectableBus2(nhv2.getId())
                .setR(3.0)
                .setX(33.0)
                .setG1(0.0)
                .setB1(386E-6 / 2)
                .setG2(0.0)
                .setB2(386E-6 / 2)
                .add();
        int zb380 = 380 * 380 / 100;
        p1.newTwoWindingsTransformer()
                .setId(prefix + "NGEN_NHV1")
                .setVoltageLevel1(vlgen.getId())
                .setBus1(ngen.getId())
                .setConnectableBus1(ngen.getId())
                .setRatedU1(24.0)
                .setVoltageLevel2(vlhv1.getId())
                .setBus2(nhv1.getId())
                .setConnectableBus2(nhv1.getId())
                .setRatedU2(400.0)
                .setR(0.24 / 1300 * zb380)
                .setX(Math.sqrt(10 * 10 - 0.24 * 0.24) / 1300 * zb380)
                .setG(0.0)
                .setB(0.0)
                .add();
        int zb150 = 150 * 150 / 100;
        TwoWindingsTransformer nhv2Nload = p2.newTwoWindingsTransformer()
                .setId(prefix + "NHV2_NLOAD")
                .setVoltageLevel1(vlhv2.getId())
                .setBus1(nhv2.getId())
                .setConnectableBus1(nhv2.getId())
                .setRatedU1(400.0)
                .setVoltageLevel2(vlload.getId())
                .setBus2(nload.getId())
                .setConnectableBus2(nload.getId())
                .setRatedU2(158.0)
                .setR(0.21 / 1000 * zb150)
                .setX(Math.sqrt(18 * 18 - 0.21 * 0.21) / 1000 * zb150)
                .setG(0.0)
                .setB(0.0)
                .add();
        double a = (158.0 / 150.0) / (400.0 / 380.0);
        nhv2Nload.newRatioTapChanger()
                .beginStep()
                .setRho(0.85f * a)
                .setR(0.0)
                .setX(0.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setRho(a)
                .setR(0.0)
                .setX(0.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setRho(1.15f * a)
                .setR(0.0)
                .setX(0.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .setTapPosition(1)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(true)
                .setTargetV(158.0)
                .setTargetDeadband(0)
                .setRegulationTerminal(nhv2Nload.getTerminal2())
                .add();
        vlload.newLoad()
                .setId(prefix + "LOAD")
                .setBus(nload.getId())
                .setConnectableBus(nload.getId())
                .setP0(600.0)
                .setQ0(200.0)
                .add();
        Generator generator = vlgen.newGenerator()
                .setId(prefix + "GEN")
                .setBus(ngen.getId())
                .setConnectableBus(ngen.getId())
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(24.5)
                .setTargetP(607.0)
                .setTargetQ(301.0)
                .add();
        generator.newMinMaxReactiveLimits()
                .setMinQ(-9999.99)
                .setMaxQ(9999.99)
                .add();
        return network;
    }
}
