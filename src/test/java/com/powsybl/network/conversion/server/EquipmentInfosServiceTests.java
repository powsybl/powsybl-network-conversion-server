/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.google.common.collect.Iterables;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import com.powsybl.network.conversion.server.dto.SubstationInfos;
import com.powsybl.network.conversion.server.dto.VoltageLevelInfos;
import com.powsybl.network.conversion.server.elasticsearch.EquipmentInfosService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.iidm.impl.NetworkImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.Assert.*;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EquipmentInfosServiceTests {

    private static final String TEST_FILE = "testCase.xiidm";

    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @Before
    public void setup() {
        equipmentInfosService.deleteAllOnInitialVariant(NETWORK_UUID);
    }

    @Test
    public void testAddDeleteEquipmentInfos() {
        List<EquipmentInfos> infos = List.of(
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).variantId(VariantManagerConstants.INITIAL_VARIANT_ID).id("id1").name("name1").type(IdentifiableType.LOAD.name()).voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).substations(Set.of(SubstationInfos.builder().id("s1").name("s1").build())).build(),
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).variantId(VariantManagerConstants.INITIAL_VARIANT_ID).id("id2").name("name2").type(IdentifiableType.LOAD.name()).voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).substations(Set.of(SubstationInfos.builder().id("s2").name("s2").build())).build()
        );

        equipmentInfosService.addAll(infos);
        Iterable<EquipmentInfos> all = equipmentInfosService.findAll(NETWORK_UUID);
        assertEquals(2, Iterables.size(all));
        List<EquipmentInfos> allList = StreamSupport.stream(all.spliterator(), false)
                .collect(Collectors.toList());
        assertEquals(infos, allList);


        equipmentInfosService.deleteAllOnInitialVariant(NETWORK_UUID);
        assertEquals(0, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));
    }

    @Test
    public void testVoltageLevels() {
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
    public void testSubstations() {
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
    public void testBadEquipmentType() {
        Identifiable<Network> network = new NetworkFactoryImpl().createNetwork("test", "test");

        String errorMessage = assertThrows(NetworkConversionException.class, () -> EquipmentInfos.getVoltageLevelsInfos(network)).getMessage();
        assertTrue(errorMessage.contains(String.format("The equipment type : %s is unknown", NetworkImpl.class.getSimpleName())));

        errorMessage = assertThrows(NetworkConversionException.class, () -> EquipmentInfos.getSubstationsInfos(network)).getMessage();
        assertTrue(errorMessage.contains(String.format("The equipment type : %s is unknown", NetworkImpl.class.getSimpleName())));
    }
}
