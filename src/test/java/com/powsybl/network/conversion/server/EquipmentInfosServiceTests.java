/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import com.powsybl.network.conversion.server.dto.SubstationInfos;
import com.powsybl.network.conversion.server.dto.VoltageLevelInfos;
import com.powsybl.network.conversion.server.elasticsearch.EquipmentInfosService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.iidm.impl.NetworkImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.powsybl.network.conversion.server.dto.EquipmentInfos.getEquipmentTypeName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EquipmentInfosServiceTests {

    private static final String TEST_FILE = "testCase.xiidm";

    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @BeforeEach
    void setup() {
        equipmentInfosService.deleteAllOnInitialVariant(NETWORK_UUID);
    }

    @Test
    void testAddDeleteEquipmentInfos() {
        List<EquipmentInfos> infos = List.of(
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).variantId(VariantManagerConstants.INITIAL_VARIANT_ID).id("id1").name("name1").type(IdentifiableType.LOAD.name()).voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).substations(Set.of(SubstationInfos.builder().id("s1").name("s1").build())).build(),
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).variantId(VariantManagerConstants.INITIAL_VARIANT_ID).id("id2").name("name2").type(IdentifiableType.LOAD.name()).voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).substations(Set.of(SubstationInfos.builder().id("s2").name("s2").build())).build()
        );
        equipmentInfosService.addAll(infos);
        List<EquipmentInfos> infosDB = equipmentInfosService.findAll(NETWORK_UUID);
        assertEquals(2, infosDB.size());
        assertEquals(infos, infosDB);
        assertEquals(infos.stream().map(i -> i.getNetworkUuid() + "_" + i.getVariantId() + "_" + i.getId()).toList(), infosDB.stream().map(i -> i.getUniqueId()).toList());

        // Change names but uniqueIds are same
        infos = List.of(
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).variantId(VariantManagerConstants.INITIAL_VARIANT_ID).id("id1").name("newName1").type(IdentifiableType.LOAD.name()).voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).substations(Set.of(SubstationInfos.builder().id("s1").name("s1").build())).build(),
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).variantId(VariantManagerConstants.INITIAL_VARIANT_ID).id("id2").name("newName2").type(IdentifiableType.LOAD.name()).voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).substations(Set.of(SubstationInfos.builder().id("s2").name("s2").build())).build()
        );
        equipmentInfosService.addAll(infos);
        infosDB = equipmentInfosService.findAll(NETWORK_UUID);
        assertEquals(2, infosDB.size());
        assertEquals(infos, infosDB);
        assertEquals(infos.stream().map(i -> i.getNetworkUuid() + "_" + i.getVariantId() + "_" + i.getId()).toList(), infosDB.stream().map(i -> i.getUniqueId()).toList());

        equipmentInfosService.deleteAllOnInitialVariant(NETWORK_UUID);
        assertEquals(0, equipmentInfosService.findAll(NETWORK_UUID).size());
    }

    @Test
    void testEquipmentInfos() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", "testCase.xiidm"));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        UUID networkUuid = UUID.randomUUID();

        VoltageLevel vl = network.getVoltageLevel("BBE1AA1");
        EquipmentInfos equipmentInfos = NetworkConversionService.toEquipmentInfos(vl, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        EquipmentInfos expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("BBE1AA1")
                .name("BBE1AA1")
                .type(IdentifiableType.VOLTAGE_LEVEL.name())
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()))
                .substations(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        Substation substation = network.getSubstation("BBE1AA");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(substation, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("BBE1AA")
                .name("BBE1AA")
                .type(IdentifiableType.SUBSTATION.name())
                .voltageLevels(Set.of(
                        VoltageLevelInfos.builder().id("BBE1TR1").name("BBE1TR1").build(),
                        VoltageLevelInfos.builder().id("BBE1TR2").name("BBE1TR2").build(),
                        VoltageLevelInfos.builder().id("BBE1TR3").name("BBE1TR3").build(),
                        VoltageLevelInfos.builder().id("BBE1AA2").name("BBE1AA2").build(),
                        VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build(),
                        VoltageLevelInfos.builder().id("BBE1AA5").name("BBE1AA5").build()))
                .substations(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        Switch switch1 = network.getSwitch("FRA1AA1_switch");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(switch1, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("FRA1AA1_switch")
                .name("FRA1AA1_switch")
                .type(IdentifiableType.SWITCH.name())
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("FRA1AA1").name("FRA1AA1").build()))
                .substations(Set.of(SubstationInfos.builder().id("FRA1AA").name("FRA1AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        Load load = network.getLoad("BBE1AA1 _load");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(load, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("BBE1AA1 _load")
                .name("BBE1AA1 _load")
                .type(IdentifiableType.LOAD.name())
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()))
                .substations(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        Bus bus = network.getBusBreakerView().getBus("BBE1AA1 ");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(bus, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("BBE1AA1 ")
                .name("BBE1AA1 ")
                .type(IdentifiableType.BUS.name())
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()))
                .substations(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        Generator generator = network.getGenerator("BBE1AA1 _generator");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(generator, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("BBE1AA1 _generator")
                .name("BBE1AA1 _generator")
                .type(IdentifiableType.GENERATOR.name())
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()))
                .substations(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        HvdcLine hvdcLine = network.getHvdcLine("FRA1AA_BBE1AA_hvdcline");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(hvdcLine, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("FRA1AA_BBE1AA_hvdcline")
                .name("FRA1AA_BBE1AA_hvdcline")
                .type(getEquipmentTypeName(hvdcLine))
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("FRA1AA1").name("FRA1AA1").build(), VoltageLevelInfos.builder().id("BBE1AA5").name("BBE1AA5").build()))
                .substations(Set.of(SubstationInfos.builder().id("FRA1AA").name("FRA1AA").build(), SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        TwoWindingsTransformer twoWindingsTransformer = network.getTwoWindingsTransformer("BBE1AA2  BBE3AA1  2");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(twoWindingsTransformer, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("BBE1AA2  BBE3AA1  2")
                .name("BBE1AA2  BBE3AA1  2")
                .type(IdentifiableType.TWO_WINDINGS_TRANSFORMER.name())
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("BBE1AA2").name("BBE1AA2").build(), VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()))
                .substations(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        ThreeWindingsTransformer threeWindingsTransformer = network.getThreeWindingsTransformer("BBE1AA_w3t");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(threeWindingsTransformer, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("BBE1AA_w3t")
                .name("BBE1AA_w3t")
                .type(IdentifiableType.THREE_WINDINGS_TRANSFORMER.name())
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("BBE1TR1").name("BBE1TR1").build(), VoltageLevelInfos.builder().id("BBE1TR2").name("BBE1TR2").build(), VoltageLevelInfos.builder().id("BBE1TR3").name("BBE1TR3").build()))
                .substations(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        Line line = network.getLine("BBE1AA1  BBE2AA1  1");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(line, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("BBE1AA1  BBE2AA1  1")
                .name("BBE1AA1  BBE2AA1  1")
                .type(IdentifiableType.LINE.name())
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build(), VoltageLevelInfos.builder().id("BBE2AA1").name("BBE2AA1").build()))
                .substations(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build(), SubstationInfos.builder().id("BBE2AA").name("BBE2AA").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);

        // test with a line belonging to one voltage level
        dataSource = new ResourceDataSource("fourSubstations_first_variant_id", new ResourceSet("", "fourSubstations_first_variant_id.xiidm"));
        network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        networkUuid = UUID.randomUUID();

        line = network.getLine("LINE_S1VL1");
        equipmentInfos = NetworkConversionService.toEquipmentInfos(line, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
        expectedEquipmentInfos = EquipmentInfos.builder()
                .networkUuid(networkUuid)
                .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
                .id("LINE_S1VL1")
                .name("LINE_S1VL1")
                .type(IdentifiableType.LINE.name())
                .voltageLevels(Set.of(VoltageLevelInfos.builder().id("S1VL1").name("S1VL1").build()))
                .substations(Set.of(SubstationInfos.builder().id("S1").name("S1").build()))
                .build();
        assertEquals(expectedEquipmentInfos, equipmentInfos);
    }

    @Test
    void testVoltageLevels() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);

        assertEquals(Set.of(
                VoltageLevelInfos.builder().id("BBE1TR1").name("BBE1TR1").build(),
                VoltageLevelInfos.builder().id("BBE1TR2").name("BBE1TR2").build(),
                VoltageLevelInfos.builder().id("BBE1TR3").name("BBE1TR3").build(),
                VoltageLevelInfos.builder().id("BBE1AA2").name("BBE1AA2").build(),
                VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build(),
                VoltageLevelInfos.builder().id("BBE1AA5").name("BBE1AA5").build()),
                EquipmentInfos.getVoltageLevelsInfos(network.getSubstation("BBE1AA")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevelsInfos(network.getVoltageLevel("BBE1AA1")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevelsInfos(network.getGenerator("BBE1AA1 _generator")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevelsInfos(network.getLoad("BBE1AA1 _load")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build(), VoltageLevelInfos.builder().id("BBE2AA1").name("BBE2AA1").build()), EquipmentInfos.getVoltageLevelsInfos(network.getLine("BBE1AA1  BBE2AA1  1")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA2").name("BBE1AA2").build(), VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevelsInfos(network.getTwoWindingsTransformer("BBE1AA2  BBE3AA1  2")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevelsInfos(network.getBusBreakerView().getBus("BBE1AA1 ")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("FRA1AA1").name("FRA1AA1").build()), EquipmentInfos.getVoltageLevelsInfos(network.getSwitch("FRA1AA1_switch")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1TR1").name("BBE1TR1").build(), VoltageLevelInfos.builder().id("BBE1TR2").name("BBE1TR2").build(), VoltageLevelInfos.builder().id("BBE1TR3").name("BBE1TR3").build()), EquipmentInfos.getVoltageLevelsInfos(network.getThreeWindingsTransformer("BBE1AA_w3t")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("FRA1AA1").name("FRA1AA1").build(), VoltageLevelInfos.builder().id("BBE1AA5").name("BBE1AA5").build()), EquipmentInfos.getVoltageLevelsInfos(network.getHvdcLine("FRA1AA_BBE1AA_hvdcline")));
    }

    @Test
    void testSubstations() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);

        assertEquals(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()), EquipmentInfos.getSubstationsInfos(network.getSubstation("BBE1AA")));
        assertEquals(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()), EquipmentInfos.getSubstationsInfos(network.getVoltageLevel("BBE1AA1")));
        assertEquals(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()), EquipmentInfos.getSubstationsInfos(network.getGenerator("BBE1AA1 _generator")));
        assertEquals(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()), EquipmentInfos.getSubstationsInfos(network.getLoad("BBE1AA1 _load")));
        assertEquals(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build(), SubstationInfos.builder().id("BBE2AA").name("BBE2AA").build()), EquipmentInfos.getSubstationsInfos(network.getLine("BBE1AA1  BBE2AA1  1")));
        assertEquals(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()), EquipmentInfos.getSubstationsInfos(network.getTwoWindingsTransformer("BBE1AA2  BBE3AA1  2")));
        assertEquals(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()), EquipmentInfos.getSubstationsInfos(network.getBusBreakerView().getBus("BBE1AA1 ")));
        assertEquals(Set.of(SubstationInfos.builder().id("FRA1AA").name("FRA1AA").build()), EquipmentInfos.getSubstationsInfos(network.getSwitch("FRA1AA1_switch")));
        assertEquals(Set.of(SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()), EquipmentInfos.getSubstationsInfos(network.getThreeWindingsTransformer("BBE1AA_w3t")));
        assertEquals(Set.of(SubstationInfos.builder().id("FRA1AA").name("FRA1AA").build(), SubstationInfos.builder().id("BBE1AA").name("BBE1AA").build()), EquipmentInfos.getSubstationsInfos(network.getHvdcLine("FRA1AA_BBE1AA_hvdcline")));
    }

    @Test
    void testBadEquipmentType() {
        Identifiable<Network> network = new NetworkFactoryImpl().createNetwork("test", "test");

        String errorMessage = assertThrows(NetworkConversionException.class, () -> EquipmentInfos.getVoltageLevelsInfos(network)).getMessage();
        assertTrue(errorMessage.contains(String.format("The equipment type : %s is unknown", NetworkImpl.class.getSimpleName())));

        errorMessage = assertThrows(NetworkConversionException.class, () -> EquipmentInfos.getSubstationsInfos(network)).getMessage();
        assertTrue(errorMessage.contains(String.format("The equipment type : %s is unknown", NetworkImpl.class.getSimpleName())));
    }

    @Test
    void testUnsupportedHybridHvdc() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);

        assertEquals(String.format("%s_%s", IdentifiableType.HVDC_LINE, HvdcConverterStation.HvdcType.VSC), EquipmentInfos.getEquipmentTypeName(network.getHvdcLine("FRA1AA_BBE1AA_hvdcline")));
        network.getVoltageLevel("FRA1AA1").newVscConverterStation()
            .setId("vsc")
            .setNode(10)
            .setLossFactor(1.1f)
            .setVoltageSetpoint(405.0)
            .setVoltageRegulatorOn(true)
            .add();
        network.getVoltageLevel("BBE1AA5").newLccConverterStation()
            .setId("lcc")
            .setNode(20)
            .setLossFactor(1.1f)
            .setPowerFactor(0.5f)
            .add();
        HvdcLine hvdcLine = network.newHvdcLine()
            .setId("HVDC")
            .setConverterStationId1("vsc")
            .setConverterStationId2("lcc")
            .setR(1)
            .setNominalV(400)
            .setConvertersMode(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER)
            .setMaxP(300.0)
            .setActivePowerSetpoint(280)
            .add();

        String errorMessage = assertThrows(NetworkConversionException.class, () -> EquipmentInfos.getEquipmentTypeName(hvdcLine)).getMessage();
        assertEquals(NetworkConversionException.createHybridHvdcUnsupported(hvdcLine.getId()).getMessage(), errorMessage);
    }
}
