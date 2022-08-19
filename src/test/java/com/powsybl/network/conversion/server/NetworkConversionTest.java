/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.cgmes.conversion.CgmesImport;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.network.conversion.server.dto.BoundaryInfos;
import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.iidm.impl.NetworkImpl;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"spring.data.elasticsearch.enabled=true"},
    classes = { EmbeddedElasticsearch.class, NetworkConversionController.class})
public class NetworkConversionTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    @Qualifier("caseServerRest")
    private RestTemplate caseServerRest;

    @MockBean
    @Qualifier("geoDataRest")
    private RestTemplate geoDataRest;

    @MockBean
    @Qualifier("reportServer")
    private RestTemplate reportServerRest;

    @Autowired
    private NetworkConversionService networkConversionService;

    @MockBean
    private NetworkStoreService networkStoreClient;

    @Test
    public void test() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/testCase.xiidm")) {
            byte[] networkByte = IOUtils.toByteArray(inputStream);
            networkConversionService.setCaseServerRest(caseServerRest);
            networkConversionService.setGeoDataServerRest(geoDataRest);
            networkConversionService.setReportServerRest(reportServerRest);
            given(caseServerRest.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                    .willReturn(new ResponseEntity<>(networkByte, HttpStatus.OK));

            ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                    new ResourceSet("", "testCase.xiidm"));
            Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);

            given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(Reporter.class), any(Boolean.class))).willReturn(network);
            UUID randomUuid = UUID.fromString("78e13f90-f351-4c2e-a383-2ad08dd5f8fb");
            given(networkStoreClient.getNetworkUuid(network)).willReturn(randomUuid);

            UUID reportUuid = UUID.fromString("11111111-f351-4c2e-a383-2ad08dd5f8fb");
            given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ReporterModel.class)))
                .willReturn(new ResponseEntity<>(HttpStatus.OK));

            mvc.perform(post("/v1/networks")
                .param("caseUuid", UUID.randomUUID().toString())
                .param("reportUuid", UUID.randomUUID().toString())
                .param("isAsyncRun", "false"))
                .andExpect(status().isOk())
                .andReturn();

            assertFalse(network.getVariantManager().getVariantIds().contains("first_variant_id"));

            String caseUuid = UUID.randomUUID().toString();
            mvc.perform(post("/v1/networks")
                .param("caseUuid", caseUuid)
                .param("variantId", "first_variant_id")
                .param("isAsyncRun", "false")
                .param("reportUuid", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
            mvc.perform(post("/v1/networks")
                .param("caseUuid", caseUuid)
                .param("variantId", "second_variant_id")
                .param("isAsyncRun", "false")
                .param("reportUuid", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

            verify(networkStoreClient).cloneVariant(randomUuid, VariantManagerConstants.INITIAL_VARIANT_ID, "first_variant_id");
            verify(networkStoreClient).cloneVariant(randomUuid, VariantManagerConstants.INITIAL_VARIANT_ID, "second_variant_id");
            //Since the test reuses the same network object, we manually clone the variants so that the rest of the test works
            network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "first_variant_id");
            network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "second_variant_id");

            mvc.perform(get("/v1/export/formats"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn();

            given(networkStoreClient.getNetwork(any(UUID.class), eq(PreloadingStrategy.COLLECTION))).willReturn(network);
            MvcResult mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "XIIDM"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                    .andReturn();

            assertEquals(String.format("attachment; filename*=UTF-8''20140116_0830_2D4_UX1_pst_%s.xiidm", VariantManagerConstants.INITIAL_VARIANT_ID), mvcResult.getResponse().getHeader("content-disposition"));
            assertTrue(mvcResult.getResponse().getContentAsString().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));

            mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "XIIDM").param("variantId", "second_variant_id"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();
            String exported1 = mvcResult.getResponse().getContentAsString();

            mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "XIIDM").param("variantId", "second_variant_id")
                    .contentType("application/json")
                    .content("{ \"iidm.export.xml.indent\" : \"false\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();
            String exported2 = mvcResult.getResponse().getContentAsString();

            assertEquals("attachment; filename*=UTF-8''20140116_0830_2D4_UX1_pst_second_variant_id.xiidm", mvcResult.getResponse().getHeader("content-disposition"));
            assertTrue(mvcResult.getResponse().getContentAsString().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));

            // takes the iidm.export.xml.indent param into account
            assertTrue(exported1.length() > exported2.length());

            // non existing variantId
            mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "XIIDM").param("variantId", "unknown_variant_id"))
                        .andExpect(status().isNotFound())
                        .andReturn();

            // non existing format
            mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "JPEG").param("variantId", "second_variant_id"))
                        .andExpect(status().isInternalServerError())
                        .andReturn();

            UUID networkUuid = UUID.fromString("f3a85c9b-9594-4e55-8ec7-07ea965d24eb");
            networkConversionService.deleteAllEquipmentInfos(networkUuid);
            List<EquipmentInfos> infos = networkConversionService.getAllEquipmentInfos(networkUuid);
            assertTrue(infos.isEmpty());

            mvc.perform(post("/v1/networks/{networkUuid}/reindex-all", networkUuid.toString()))
                .andExpect(status().isOk())
                .andReturn();
            infos = networkConversionService.getAllEquipmentInfos(networkUuid);
            assertEquals(77, infos.size());

            given(caseServerRest.getForEntity("/v1/cases/" + caseUuid + "/format", String.class)).willReturn(ResponseEntity.ok("XIIDM"));
            //given(networkConversionService.getCaseFormat(caseUuidFormat)).willReturn("XIIDM");
            // test get case import parameters
            mvcResult = mvc.perform(get("/v1/cases/{caseUuid}/import-parameters", caseUuid))
                .andExpect(status().isOk())
                .andReturn();

            assertTrue(mvcResult.getResponse().getContentAsString().startsWith("{\"formatName\":\"XIIDM\",\"parameters\":"));

            Map<String, Object> importParameters = new HashMap<>();
            importParameters.put("randomImportParameters", "randomImportValue");

            given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(Reporter.class), any(Properties.class), any(Boolean.class))).willReturn(network);

            mvc.perform(post("/v1/networks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(importParameters))
                    .param("caseUuid", caseUuid)
                    .param("variantId", "import_params_variant_id")
                    .param("reportUuid", UUID.randomUUID().toString())
                    .param("isRunAsync", "false"))
                    .andExpect(status().isOk());
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

        MvcResult mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", testNetworkId1.toString(), "XIIDM")
                .param("networkUuid", testNetworkId2.toString())
                .param("networkUuid", testNetworkId3.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();

        assertEquals(String.format("attachment; filename*=UTF-8''merged_network_%s.xiidm", VariantManagerConstants.INITIAL_VARIANT_ID), mvcResult.getResponse().getHeader("content-disposition"));
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

        Network network = new CgmesImport().importData(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource(), new NetworkFactoryImpl(), null);
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class))).willReturn(network);
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(Reporter.class), any(Boolean.class))).willReturn(network);
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

    @Test
    public void testSendReport() throws Exception {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e7");
        UUID reportUuid = UUID.fromString("11111111-7977-4592-ba19-88027e4254e7");
        networkConversionService.setReportServerRest(reportServerRest);

        Network network = new CgmesImport().importData(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource(), new NetworkFactoryImpl(), null);
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReporterModel.class), any(Boolean.class))).willAnswer((Answer<Network>) invocationOnMock -> {
            var reporter = invocationOnMock.getArgument(1, ReporterModel.class);
            reporter.addSubReporter(new ReporterModel("test", "test"));
            return network;
        });
        given(networkStoreClient.getNetworkUuid(network)).willReturn(networkUuid);
        given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ReporterModel.class)))
            .willReturn(new ResponseEntity<>(HttpStatus.OK));

        MvcResult mvcResult = mvc.perform(post("/v1/networks/")
            .param("caseUuid", caseUuid.toString())
            .param("reportUuid", reportUuid.toString())
            .param("isAsyncRun", "false"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("{\"networkUuid\":\"" + networkUuid + "\",\"networkId\":\"urn:uuid:d400c631-75a0-4c30-8aed-832b0d282e73\"}",
                mvcResult.getResponse().getContentAsString());
    }

    @Test
    public void testImportWithError() {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e7");
        UUID reportUuid = UUID.fromString("11111111-7977-4592-ba19-88027e4254e7");
        networkConversionService.setReportServerRest(reportServerRest);

        Network network = createNetwork("test");
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReporterModel.class), any(Boolean.class)))
                .willThrow(NetworkConversionException.createFailedNetworkSaving(networkUuid, NetworkConversionException.createEquipmentTypeUnknown(NetworkImpl.class.getSimpleName())));
        given(networkStoreClient.getNetworkUuid(network)).willReturn(networkUuid);
        given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ReporterModel.class)))
                .willReturn(new ResponseEntity<>(HttpStatus.OK));

        String message = assertThrows(NetworkConversionException.class, () -> networkConversionService.importCase(caseUuid, null, reportUuid, null)).getMessage();
        assertTrue(message.contains(String.format("The save of network '%s' has failed", networkUuid)));
    }

    @Test
    public void testFlushNetworkWithError() {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e7");
        UUID reportUuid = UUID.fromString("11111111-7977-4592-ba19-88027e4254e7");
        networkConversionService.setReportServerRest(reportServerRest);

        Network network = createNetwork("test");
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(Reporter.class), any(Boolean.class))).willReturn(network);
        doThrow(NetworkConversionException.createFailedNetworkSaving(networkUuid, NetworkConversionException.createEquipmentTypeUnknown(NetworkImpl.class.getSimpleName())))
                .when(networkStoreClient).flush(network);
        given(networkStoreClient.getNetworkUuid(network)).willReturn(networkUuid);
        given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ReporterModel.class)))
                .willReturn(new ResponseEntity<>(HttpStatus.OK));
        given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .willReturn(new ResponseEntity<>(HttpStatus.OK));

        String message = assertThrows(NetworkConversionException.class, () -> networkConversionService.importCase(caseUuid, null, reportUuid, null)).getMessage();
        assertTrue(message.contains(String.format("The save of network '%s' has failed", networkUuid)));
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
