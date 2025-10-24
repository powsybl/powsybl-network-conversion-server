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
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.conversion.server.dto.BoundaryInfos;
import com.powsybl.network.conversion.server.dto.CaseInfos;
import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import com.powsybl.network.conversion.server.dto.TombstonedEquipmentInfos;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.iidm.impl.NetworkImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.messaging.Message;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.powsybl.network.conversion.server.NetworkConversionService.TYPES_FOR_INDEXING;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfigurationWithTestChannel
class NetworkConversionTest {

    private static final String IMPORT_CASE_ERROR_MESSAGE = "An error occured while importing case";

    private static final Map<String, Object> EMPTY_PARAMETERS = new HashMap<>();

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

    @Autowired
    private OutputDestination output;

    @BeforeEach
    void setup() {
        networkConversionService.setCaseServerRest(caseServerRest);
        networkConversionService.setGeoDataServerRest(geoDataRest);
        networkConversionService.setReportServerRest(reportServerRest);
    }

    @Test
    void test() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/testCase.xiidm")) {
            byte[] networkByte = inputStream.readAllBytes();

            given(caseServerRest.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                    .willReturn(new ResponseEntity<>(networkByte, HttpStatus.OK));

            ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                    new ResourceSet("", "testCase.xiidm"));
            Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);

            given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReportNode.class), any(Boolean.class))).willReturn(network);
            UUID randomUuid = UUID.fromString("78e13f90-f351-4c2e-a383-2ad08dd5f8fb");
            given(networkStoreClient.getNetworkUuid(network)).willReturn(randomUuid);

            UUID reportUuid = UUID.fromString("11111111-f351-4c2e-a383-2ad08dd5f8fb");
            given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ReportNode.class)))
                .willReturn(new ResponseEntity<>(HttpStatus.OK));

            String caseUuid = UUID.randomUUID().toString();
            given(caseServerRest.exchange(eq("/v1/cases/{caseUuid}/datasource/baseName"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class), eq(UUID.fromString(caseUuid))))
                .willReturn(ResponseEntity.ok("testCase"));
            given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid.toString()), "testCase.xiidm", "XIIDM")));

            MvcResult mvcResult = mvc.perform(post("/v1/networks")
                .param("caseUuid", caseUuid)
                .param("reportUuid", UUID.randomUUID().toString())
                .param("isAsyncRun", "false")
                .param("caseFormat", "XIIDM"))
                .andExpect(status().isOk())
                .andReturn();

            assertEquals("{\"networkUuid\":\"" + randomUuid + "\",\"networkId\":\"20140116_0830_2D4_UX1_pst\"}",
                    mvcResult.getResponse().getContentAsString());
            assertFalse(network.getVariantManager().getVariantIds().contains("first_variant_id"));

            mvc.perform(post("/v1/networks")
                .param("caseUuid", caseUuid)
                .param("variantId", "first_variant_id")
                .param("isAsyncRun", "false")
                .param("reportUuid", UUID.randomUUID().toString())
                .param("caseFormat", "XIIDM"))
                .andExpect(status().isOk());
            mvc.perform(post("/v1/networks")
                .param("caseUuid", caseUuid)
                .param("variantId", "second_variant_id")
                .param("isAsyncRun", "false")
                .param("reportUuid", UUID.randomUUID().toString())
                .param("caseFormat", "XIIDM"))
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

            UUID notFoundNetworkUuid = UUID.randomUUID();
            given(networkStoreClient.getNetwork(notFoundNetworkUuid)).willThrow(new PowsyblException("Network " + notFoundNetworkUuid.toString() + " not found"));
            given(networkStoreClient.getNetwork(any(UUID.class), eq(PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW))).willReturn(network);
            mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "XIIDM"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                    .andReturn();
            assertTrue(Objects.requireNonNull(mvcResult.getResponse().getHeader("content-disposition")).contains("attachment;"));
            assertTrue(Objects.requireNonNull(mvcResult.getResponse().getHeader("content-disposition")).contains(String.format("filename*=UTF-8''20140116_0830_2D4_UX1_pst_%s.zip", VariantManagerConstants.INITIAL_VARIANT_ID)));

            byte[] zipBytes = mvcResult.getResponse().getContentAsByteArray();
            assertNotNull(zipBytes);
            assertTrue(zipBytes.length > 0);
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                while ((zis.getNextEntry()) != null) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(content.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
                    assertTrue(content.contains("id=\"20140116_0830_2D4_UX1_pst\""));
                }
            }

            mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "XIIDM").param("variantId", "second_variant_id"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();
            String exported1 = mvcResult.getResponse().getContentAsString();

            mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "XIIDM").param("variantId", "second_variant_id")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{ \"iidm.export.xml.indent\" : \"false\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();
            String exported2 = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertTrue(Objects.requireNonNull(mvcResult.getResponse().getHeader("content-disposition")).contains("attachment;"));
            assertTrue(Objects.requireNonNull(mvcResult.getResponse().getHeader("content-disposition")).contains("filename*=UTF-8''20140116_0830_2D4_UX1_pst_second_variant_id.zip"));

            // takes the iidm.export.xml.indent param into account
            assertTrue(exported1.length() > exported2.length());

            //with fileName
            mvcResult = mvc.perform(post("/v1/networks/{networkUuid}/export/{format}?fileName=" + "studyName_Root", UUID.randomUUID().toString(), "XIIDM").param("variantId", "second_variant_id"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                    .andReturn();

            assertTrue(Objects.requireNonNull(mvcResult.getResponse().getHeader("content-disposition")).contains("attachment;"));
            assertTrue(Objects.requireNonNull(mvcResult.getResponse().getHeader("content-disposition")).contains("filename*=UTF-8''studyName_Root.zip"));

            // non existing variantId
            mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "XIIDM").param("variantId", "unknown_variant_id"))
                .andExpect(status().isNotFound())
                .andReturn();

            // non existing format
            mvc.perform(post("/v1/networks/{networkUuid}/export/{format}", UUID.randomUUID().toString(), "JPEG").param("variantId", "second_variant_id"))
                .andExpect(status().isInternalServerError())
                .andReturn();

            UUID networkUuid = UUID.fromString("f3a85c9b-9594-4e55-8ec7-07ea965d24eb");
            networkConversionService.deleteAllEquipmentInfosByNetworkUuid(networkUuid);
            List<EquipmentInfos> infos = networkConversionService.getAllEquipmentInfos(networkUuid);
            assertTrue(infos.isEmpty());

            mvc.perform(head("/v1/networks/{networkUuid}/indexed-equipments", notFoundNetworkUuid.toString()))
                .andExpect(status().isNotFound())
                .andReturn();

            mvc.perform(head("/v1/networks/{networkUuid}/indexed-equipments", networkUuid.toString()))
                .andExpect(status().isNoContent())
                .andReturn();

            mvc.perform(post("/v1/networks/{networkUuid}/reindex-all", networkUuid.toString()))
                .andExpect(status().isOk())
                .andReturn();
            infos = networkConversionService.getAllEquipmentInfos(networkUuid);
            // exclude switch, bus bar section and bus since it is not indexed
            assertEquals(74, infos.size());
            assertTrue(infos.stream()
                    .allMatch(equipmentInfos -> TYPES_FOR_INDEXING.contains(getExtendedIdentifiableType(equipmentInfos))));

            mvc.perform(head("/v1/networks/{networkUuid}/indexed-equipments", networkUuid.toString()))
                .andExpect(status().isOk())
                .andReturn();

            given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid), "testCase", "XIIDM")));

            // test get case import parameters
            mvcResult = mvc.perform(get("/v1/cases/{caseUuid}/import-parameters", caseUuid))
                .andExpect(status().isOk())
                .andReturn();

            assertTrue(mvcResult.getResponse().getContentAsString().startsWith("{\"formatName\":\"XIIDM\",\"parameters\":"));

            // sync import with import parameters
            Map<String, Object> importParameters = new HashMap<>();
            importParameters.put("randomImportParameters", "randomImportValue");

            given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReportNode.class), any(Properties.class), any(Boolean.class))).willReturn(network);

            mvc.perform(post("/v1/networks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(importParameters))
                    .param("caseUuid", caseUuid)
                    .param("variantId", "import_params_variant_id")
                    .param("reportUuid", UUID.randomUUID().toString())
                    .param("isAsyncRun", "false")
                    .param("caseFormat", "XIIDM"))
                    .andExpect(status().isOk());

            // test without report
            mvc.perform(post("/v1/networks")
                            .param("caseUuid", caseUuid)
                            .param("isAsyncRun", "false")
                            .param("caseFormat", "XIIDM"))
                    .andExpect(status().isOk());

            // test without report and with an error at flush
            doThrow(NetworkConversionException.createFailedNetworkSaving(networkUuid, NetworkConversionException.createEquipmentTypeUnknown("?")))
                    .when(networkStoreClient).flush(network);
            mvc.perform(post("/v1/networks")
                            .param("caseUuid", caseUuid)
                            .param("isAsyncRun", "false")
                            .param("caseFormat", "XIIDM"))
                    .andExpect(status().isInternalServerError());
        }
    }

    private static IdentifiableType getExtendedIdentifiableType(EquipmentInfos equipmentInfos) {
        String type = equipmentInfos.getType();
        if (type.equals("HVDC_LINE_VSC") || type.equals("HVDC_LINE_LCC")) {
            return IdentifiableType.HVDC_LINE;
        }
        return IdentifiableType.valueOf(type);
    }

    @Test
    void testAsyncImport() throws Exception {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", "testCase.xiidm"));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        UUID randomUuid = UUID.fromString("78e13f90-f351-4c2e-a383-2ad08dd5f8fb");
        String caseUuid = UUID.randomUUID().toString();
        String receiver = "test receiver";
        given(networkStoreClient.getNetworkUuid(network)).willReturn(randomUuid);
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReportNode.class), any(Properties.class), any(Boolean.class))).willReturn(network);
        given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid), "testCase", "XIIDM")));
        given(caseServerRest.exchange(eq("/v1/cases/{caseUuid}/datasource/baseName"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class), eq(UUID.fromString(caseUuid))))
            .willReturn(ResponseEntity.ok("testCase"));

        Map<String, Object> importParameters = new HashMap<>();
        importParameters.put("randomImportParameters", "randomImportValue");

        mvc.perform(post("/v1/networks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(importParameters))
                .param("caseUuid", caseUuid)
                .param("variantId", "async_variant_id")
                .param("reportUuid", UUID.randomUUID().toString())
                .param("receiver", receiver)
                .param("caseFormat", "XIIDM"))
                .andExpect(status().isOk());

        Message<byte[]> message = output.receive(1000, "case.import.succeeded");
        assertEquals(randomUuid.toString(), message.getHeaders().get(NotificationService.HEADER_NETWORK_UUID));
        assertEquals(receiver, message.getHeaders().get(NotificationService.HEADER_RECEIVER));
        assertEquals("20140116_0830_2D4_UX1_pst", message.getHeaders().get(NotificationService.HEADER_NETWORK_ID));
    }

    @Test
    void testFailedAsyncImport() throws Exception {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", "testCase.xiidm"));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        UUID randomUuid = UUID.fromString("78e13f90-f351-4c2e-a383-2ad08dd5f8fb");
        String caseUuid = UUID.randomUUID().toString();
        String receiver = "test receiver";
        given(networkStoreClient.getNetworkUuid(network)).willReturn(randomUuid);
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReportNode.class), any(Properties.class), any(Boolean.class))).willThrow(new NullPointerException(IMPORT_CASE_ERROR_MESSAGE));
        given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid), "testCase", "XIIDM")));
        given(caseServerRest.exchange(eq("/v1/cases/{caseUuid}/datasource/baseName"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class), eq(UUID.fromString(caseUuid))))
            .willReturn(ResponseEntity.ok("testCase"));

        Map<String, Object> importParameters = new HashMap<>();
        importParameters.put("randomImportParameters", "randomImportValue");

        mvc.perform(post("/v1/networks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(importParameters))
                .param("caseUuid", caseUuid)
                .param("variantId", "async_failure_variant_id")
                .param("reportUuid", UUID.randomUUID().toString())
                .param("receiver", receiver)
                .param("caseFormat", "XIIDM"))
                .andExpect(status().isOk());

        Message<byte[]> message = output.receive(1000, "case.import.start");
        assertEquals(receiver, message.getHeaders().get(NotificationService.HEADER_RECEIVER));
    }

    @Test
    void testCgmesCaseDataSource() throws Exception {
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
                eq(Resource.class)))
                .willReturn(ResponseEntity.ok(new InputStreamResource(new ByteArrayInputStream(sshContent))));

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
    void testImportCgmesCase() throws Exception {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e7");

        List<BoundaryInfos> boundaries = new ArrayList<>();
        String eqbdContent = "fake content of eqbd boundary";
        String tpbdContent = "fake content of tpbd boundary";
        boundaries.add(new BoundaryInfos("urn:uuid:f1582c44-d9e2-4ea0-afdc-dba189ab4358", "20201121T0000Z__ENTSOE_EQBD_003.xml", eqbdContent));
        boundaries.add(new BoundaryInfos("urn:uuid:3e3f7738-aab9-4284-a965-71d5cd151f71", "20201205T1000Z__ENTSOE_TPBD_004.xml", tpbdContent));

        Network network = new CgmesImport().importData(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource(), new NetworkFactoryImpl(), null);
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class))).willReturn(network);
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReportNode.class), any(Boolean.class))).willReturn(network);
        given(networkStoreClient.getNetworkUuid(network)).willReturn(networkUuid);
        given(caseServerRest.exchange(eq("/v1/cases/{caseUuid}/datasource/baseName"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class), eq(caseUuid)))
            .willReturn(ResponseEntity.ok("testCase"));
        given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid.toString()), "testCase", "XIIDM")));

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
    void testSendReport() throws Exception {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e7");
        UUID reportUuid = UUID.fromString("11111111-7977-4592-ba19-88027e4254e7");
        networkConversionService.setReportServerRest(reportServerRest);

        Network network = new CgmesImport().importData(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource(), new NetworkFactoryImpl(), null);
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReportNode.class), any(Boolean.class))).willAnswer((Answer<Network>) invocationOnMock -> {
            var reportNode = invocationOnMock.getArgument(1, ReportNode.class);
            reportNode.newReportNode()
                    .withMessageTemplate("test")
                    .add();
            return network;
        });
        given(networkStoreClient.getNetworkUuid(network)).willReturn(networkUuid);
        given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ReportNode.class)))
            .willReturn(new ResponseEntity<>(HttpStatus.OK));
        given(caseServerRest.exchange(eq("/v1/cases/{caseUuid}/datasource/baseName"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class), eq(caseUuid)))
            .willReturn(ResponseEntity.ok("testCase"));
        given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid.toString()), "testCase", "XIIDM")));

        MvcResult mvcResult = mvc.perform(post("/v1/networks")
            .param("caseUuid", caseUuid.toString())
            .param("reportUuid", reportUuid.toString())
            .param("isAsyncRun", "false")
            .param("caseFormat", "XIIDM"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("{\"networkUuid\":\"" + networkUuid + "\",\"networkId\":\"urn:uuid:d400c631-75a0-4c30-8aed-832b0d282e73\"}",
                mvcResult.getResponse().getContentAsString());
    }

    @Test
    void testImportWithError() {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e7");
        UUID reportUuid = UUID.fromString("11111111-7977-4592-ba19-88027e4254e7");
        networkConversionService.setReportServerRest(reportServerRest);
        given(caseServerRest.exchange(eq("/v1/cases/{caseUuid}/datasource/baseName"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class), eq(caseUuid)))
            .willReturn(ResponseEntity.ok("testCase"));

        Network network = createNetwork("test");
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReportNode.class), any(Boolean.class)))
                .willThrow(NetworkConversionException.createFailedNetworkSaving(networkUuid, NetworkConversionException.createEquipmentTypeUnknown(NetworkImpl.class.getSimpleName())));
        given(networkStoreClient.getNetworkUuid(network)).willReturn(networkUuid);
        given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ReportNode.class)))
                .willReturn(new ResponseEntity<>(HttpStatus.OK));
        given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid.toString()), "testCase", "XIIDM")));

        String message = assertThrows(NetworkConversionException.class, () -> networkConversionService.importCase(caseUuid, null, reportUuid, "XIIDM", EMPTY_PARAMETERS)).getMessage();
        assertTrue(message.contains(String.format("The save of network '%s' has failed", networkUuid)));
    }

    @Test
    void testFlushNetworkWithError() {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        UUID networkUuid = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e7");
        UUID reportUuid = UUID.fromString("11111111-7977-4592-ba19-88027e4254e7");
        networkConversionService.setReportServerRest(reportServerRest);

        Network network = createNetwork("test");
        given(networkStoreClient.importNetwork(any(ReadOnlyDataSource.class), any(ReportNode.class), any(Boolean.class))).willReturn(network);
        doThrow(NetworkConversionException.createFailedNetworkSaving(networkUuid, NetworkConversionException.createEquipmentTypeUnknown(NetworkImpl.class.getSimpleName())))
                .when(networkStoreClient).flush(network);
        given(networkStoreClient.getNetworkUuid(network)).willReturn(networkUuid);
        given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ReportNode.class)))
                .willReturn(new ResponseEntity<>(HttpStatus.OK));
        given(reportServerRest.exchange(eq("/v1/reports/" + reportUuid), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .willReturn(new ResponseEntity<>(HttpStatus.OK));
        given(caseServerRest.exchange(eq("/v1/cases/{caseUuid}/datasource/baseName"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class), eq(caseUuid)))
            .willReturn(ResponseEntity.ok("testCase"));
        given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid.toString()), "testCase", "XIIDM")));

        String message = assertThrows(NetworkConversionException.class, () -> networkConversionService.importCase(caseUuid, null, reportUuid, "XIIDM", EMPTY_PARAMETERS)).getMessage();
        assertTrue(message.contains(String.format("The save of network '%s' has failed", networkUuid)));
    }

    @Test
    void testReindexAllVariants() {
        Network network = createNetwork("test");
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "first_variant_id");
        network.getVariantManager().setWorkingVariant("first_variant_id");
        network.getLoad("testLOAD").remove();
        network.getVoltageLevel("testVLLOAD").newLoad()
                .setId("newLoad")
                .setBus("testNLOAD")
                .setConnectableBus("testNLOAD")
                .setP0(600.0)
                .setQ0(200.0)
                .add();
        network.getVariantManager().cloneVariant("first_variant_id", "second_variant_id");
        network.getVariantManager().setWorkingVariant("second_variant_id");
        network.getTwoWindingsTransformer("testNGEN_NHV1").setName("test1");
        network.getGenerator("testGEN").setMaxP(12.36);
        network.getVariantManager().cloneVariant("second_variant_id", "third_variant_id");
        network.getVariantManager().setWorkingVariant("third_variant_id");
        network.getSubstation("testP1").setName("newName");
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);

        UUID networkUuid = UUID.fromString("78e13f90-f351-4c2e-a383-2ad08dd5f8fb");
        given(networkStoreClient.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network);

        networkConversionService.reindexAllEquipments(networkUuid);
        // Initial variant has 12 indexed elements (no switches, bbs, bus)
        List<EquipmentInfos> equipmentInfos = networkConversionService.getAllEquipmentInfosByNetworkUuidAndVariantId(networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        List<TombstonedEquipmentInfos> tombstonedEquipmentInfos = networkConversionService.getAllTombstonedEquipmentInfosByNetworkUuidAndVariantId(networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        assertEquals(12, equipmentInfos.size());
        assertTrue(equipmentInfos.stream()
                .allMatch(equipments -> TYPES_FOR_INDEXING.contains(getExtendedIdentifiableType(equipments))));
        assertEquals(0, tombstonedEquipmentInfos.size());
        // Removed 1 load, added 1 load
        equipmentInfos = networkConversionService.getAllEquipmentInfosByNetworkUuidAndVariantId(networkUuid, "first_variant_id");
        tombstonedEquipmentInfos = networkConversionService.getAllTombstonedEquipmentInfosByNetworkUuidAndVariantId(networkUuid, "first_variant_id");
        assertEquals(1, equipmentInfos.size());
        assertTrue(equipmentInfos.stream()
                .allMatch(equipments -> TYPES_FOR_INDEXING.contains(getExtendedIdentifiableType(equipments))));
        assertEquals(1, tombstonedEquipmentInfos.size());
        // Rename 2WT and change unindexed generator attribute (with additional changes from previous variant)
        equipmentInfos = networkConversionService.getAllEquipmentInfosByNetworkUuidAndVariantId(networkUuid, "second_variant_id");
        tombstonedEquipmentInfos = networkConversionService.getAllTombstonedEquipmentInfosByNetworkUuidAndVariantId(networkUuid, "second_variant_id");
        assertEquals(2, equipmentInfos.size());
        assertTrue(equipmentInfos.stream()
                .allMatch(equipments -> TYPES_FOR_INDEXING.contains(getExtendedIdentifiableType(equipments))));
        assertEquals(1, tombstonedEquipmentInfos.size());
        // Rename substation changes all equipment infos of equipments contained in the substation
        equipmentInfos = networkConversionService.getAllEquipmentInfosByNetworkUuidAndVariantId(networkUuid, "third_variant_id");
        tombstonedEquipmentInfos = networkConversionService.getAllTombstonedEquipmentInfosByNetworkUuidAndVariantId(networkUuid, "third_variant_id");
        assertEquals(8, equipmentInfos.size());
        assertTrue(equipmentInfos.stream()
                .allMatch(equipments -> TYPES_FOR_INDEXING.contains(getExtendedIdentifiableType(equipments))));
        assertEquals(1, tombstonedEquipmentInfos.size());
    }

    @Test
    void testReindexThrows() {
        UUID networkUuid = UUID.fromString("78e13f90-f351-4c2e-a383-2ad08dd5f8fb");
        given(networkStoreClient.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willThrow(new PowsyblException("Network not found"));
        NetworkConversionException e = assertThrows(NetworkConversionException.class, () -> networkConversionService.reindexAllEquipments(networkUuid));
        assertEquals("Reindex of network '" + networkUuid + "' has failed", e.getMessage());
    }

    @Test
    void testExportEndpoint() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/testCase.xiidm")) {
            assertNotNull(inputStream);
            byte[] networkByte = inputStream.readAllBytes();
            String caseUuid = UUID.randomUUID().toString();
            UUID randomUuid = UUID.fromString("78e13f90-f351-4c2e-a383-2ad08dd5f8fb");

            given(caseServerRest.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .willAnswer(invocation -> ResponseEntity.ok(new InputStreamResource(new ByteArrayInputStream(networkByte))));

            // test convert format
            String path = UriComponentsBuilder.fromPath("/v1/cases/{caseUuid}/datasource/list")
                .queryParam("regex", "(?i)^.*\\.(XML|ZIP)$")
                .buildAndExpand(caseUuid)
                .toUriString();
            given(caseServerRest.exchange(eq(path),
                eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .willReturn(ResponseEntity.ok(Collections.emptySet()));

            String path2 = UriComponentsBuilder.fromPath("/v1/cases/{caseUuid}/datasource/exists?fileName=testCase.xiidm")
                .buildAndExpand(caseUuid)
                .toUriString();
            given(caseServerRest.exchange(eq(path2), eq(HttpMethod.GET), any(HttpEntity.class), eq(Boolean.class)))
                .willReturn(ResponseEntity.ok(true));

            mockCaseExist("txt", caseUuid, false);
            mockCaseExist("uct", caseUuid, false);
            mockCaseExist("UCT", caseUuid, false);
            mockCaseExist("mat", caseUuid, false);
            mockCaseExist("biidm", caseUuid, false);
            mockCaseExist("bin", caseUuid, false);
            mockCaseExist("jiidm", caseUuid, false);
            mockCaseExist("json", caseUuid, false);
            mockCaseExist("xiidm", caseUuid, true);
            mockCaseExist("iidm", caseUuid, true);
            mockCaseExist("xml", caseUuid, true);
            mockCaseExist("csv", "_mapping", caseUuid, false);

            given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid), "testCase", "XIIDM")));

            // convert to iidm
            MvcResult mvcResult1 = mvc.perform(post("/v1/cases/{caseUuid}/convert/{format}", caseUuid, "XIIDM")
                    .param("fileName", "testCase")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{ \"iidm.export.xml.indent\" : \"false\"}"))
                .andExpect(status().isOk())
                .andReturn();

            assertTrue(Objects.requireNonNull(mvcResult1.getResponse().getHeader("content-disposition")).contains("attachment;"));
            assertTrue(Objects.requireNonNull(mvcResult1.getResponse().getHeader("content-disposition")).contains("filename=\"testCase.xiidm\""));
            assertTrue(mvcResult1.getResponse().getContentAsString().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));

            // convert to biidm
            MvcResult mvcResult2 = mvc.perform(post("/v1/cases/{caseUuid}/convert/{format}", caseUuid, "BIIDM")
                    .param("fileName", "testCase")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{ \"iidm.export.xml.indent\" : \"false\"}"))
                .andExpect(status().isOk())
                .andReturn();
            assertTrue(Objects.requireNonNull(mvcResult2.getResponse().getHeader("content-disposition")).contains("attachment;"));
            assertTrue(Objects.requireNonNull(mvcResult2.getResponse().getHeader("content-disposition")).contains("filename=\"testCase.biidm\""));
            assertTrue(mvcResult2.getResponse().getContentAsString().startsWith("Binary IIDM"));

            // fail because case not found
            MvcResult fail = mvc.perform(post("/v1/cases/{caseUuid}/convert/{format}", randomUuid, "BIIDM")
                    .param("fileName", "testCase")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{ \"iidm.export.xml.indent\" : \"false\"}"))
                .andExpect(status().isInternalServerError())
                .andReturn();
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), fail.getResponse().getStatus());
            assertEquals("Case export failed", fail.getResponse().getContentAsString());

            // fail because network format does not exist
            mvc.perform(post("/v1/cases/{caseUuid}/convert/{format}", caseUuid, "JPEG")
                    .param("fileName", "testCase")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{ \"iidm.export.xml.indent\" : \"false\"}"))
                .andExpect(status().isInternalServerError())
                .andReturn();

            // export case with an absolut path as fileName
            mvc.perform(post("/v1/cases/{caseUuid}/convert/{format}", caseUuid, "XIIDM")
                            .param("fileName", "/tmp/testCase")
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .content("{ \"iidm.export.xml.indent\" : \"false\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            // check that no temporary export directory is still present after conversions
            assertFalse(Files.list(Paths.get("/tmp"))
                    .anyMatch(path3 -> Files.isDirectory(path3) &&
                            path3.getFileName().toString().startsWith("export_")));
        }
    }

    @Test
    void testConvertToCgmes() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/fourSubstations_first_variant_id.xiidm")) {
            assertNotNull(inputStream);
            byte[] networkByte = inputStream.readAllBytes();
            String caseUuid = UUID.randomUUID().toString();
            given(caseServerRest.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                    .willAnswer(invocation -> ResponseEntity.ok(new InputStreamResource(new ByteArrayInputStream(networkByte))));

            // test convert format
            String path = UriComponentsBuilder.fromPath("/v1/cases/{caseUuid}/datasource/list")
                .queryParam("regex", "(?i)^.*\\.(XML|ZIP)$")
                .buildAndExpand(caseUuid)
                .toUriString();
            given(caseServerRest.exchange(eq(path),
                eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .willReturn(ResponseEntity.ok(Collections.emptySet()));

            String path2 = UriComponentsBuilder.fromPath("/v1/cases/{caseUuid}/datasource/exists?fileName=fourSubstations_first_variant_id.xiidm")
                .buildAndExpand(caseUuid)
                .toUriString();
            given(caseServerRest.exchange(eq(path2), eq(HttpMethod.GET), any(HttpEntity.class), eq(Boolean.class)))
                .willReturn(ResponseEntity.ok(true));

            mockCaseExist("txt", caseUuid, false);
            mockCaseExist("uct", caseUuid, false);
            mockCaseExist("UCT", caseUuid, false);
            mockCaseExist("mat", caseUuid, false);
            mockCaseExist("biidm", caseUuid, false);
            mockCaseExist("bin", caseUuid, false);
            mockCaseExist("jiidm", caseUuid, false);
            mockCaseExist("json", caseUuid, false);
            mockCaseExist("xiidm", caseUuid, true);
            mockCaseExist("iidm", caseUuid, true);
            mockCaseExist("xml", caseUuid, true);
            mockCaseExist("csv", "_mapping", caseUuid, false);

            given(caseServerRest.getForEntity(eq("/v1/cases/" + caseUuid + "/infos"), any())).willReturn(ResponseEntity.ok(new CaseInfos(UUID.fromString(caseUuid), "testCase", "XIIDM")));

            // convert to cgmes
            MvcResult mvcResult3 = mvc.perform(post("/v1/cases/{caseUuid}/convert/{format}", caseUuid, "CGMES")
                    .param("fileName", "testCase")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{ \"iidm.export.xml.indent\" : \"false\"}"))
                .andExpect(status().isOk())
                .andReturn();
            byte[] bytes = mvcResult3.getResponse().getContentAsByteArray();
            List<String> filenames = new ArrayList<>();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    filenames.add(entry.getName());
                }
            }
            assertTrue(filenames.containsAll(List.of("testCase_EQ.xml", "testCase_SV.xml", "testCase_SSH.xml", "testCase_TP.xml")));
        }
    }

    @Test
    void testTieLinesAreNotIndexed() throws Exception {
        Network network = Network.create("test", "test");
        Substation s1 = network.newSubstation().setId("S1").add();
        Substation s2 = network.newSubstation().setId("S2").add();
        VoltageLevel vl1 = s1.newVoltageLevel().setId("VL").setNominalV(1f).setTopologyKind(TopologyKind.NODE_BREAKER).add();
        VoltageLevel vl2 = s2.newVoltageLevel().setId("VL2").setNominalV(1f).setTopologyKind(TopologyKind.NODE_BREAKER).add();
        DanglingLine dl1 = vl1.newDanglingLine().setId("DL1").setNode(0).setP0(0.0).setQ0(0.0).setR(1.5).setX(13.0).setG(0.0).setB(1e-6).add();
        DanglingLine dl2 = vl2.newDanglingLine().setId("DL2").setNode(0).setP0(0.0).setQ0(0.0).setR(1.5).setX(13.0).setG(0.0).setB(1e-6).add();
        network.newTieLine().setId("TL").setDanglingLine1(dl1.getId()).setDanglingLine2(dl2.getId()).add();

        UUID networkUuid = UUID.randomUUID();
        given(networkStoreClient.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network);

        mvc.perform(post("/v1/networks/{networkUuid}/reindex-all", networkUuid.toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<EquipmentInfos> infos = networkConversionService.getAllEquipmentInfos(networkUuid);
        assertTrue(infos.stream()
                .noneMatch(equipmentInfos -> equipmentInfos.getType().equals(IdentifiableType.TIE_LINE.name())));
        assertTrue(infos.stream()
                .allMatch(equipmentInfos -> TYPES_FOR_INDEXING.contains(getExtendedIdentifiableType(equipmentInfos))));
    }

    private static Network createNetwork(String prefix) {
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

    private void mockCaseExist(String ext, String caseUuid, boolean returnValue) {
        mockCaseExist(ext, null, caseUuid, returnValue);
    }

    private void mockCaseExist(String ext, String suffix, String caseUuid, boolean returnValue) {
        String path = UriComponentsBuilder.fromPath("/v1/cases/{caseUuid}/datasource/exists")
            .queryParam("suffix", suffix)
            .queryParam("ext", ext)
            .buildAndExpand(caseUuid)
            .toUriString();
        given(caseServerRest.exchange(eq(path), eq(HttpMethod.GET), any(HttpEntity.class), eq(Boolean.class)))
            .willReturn(ResponseEntity.ok(returnValue));
    }
}
