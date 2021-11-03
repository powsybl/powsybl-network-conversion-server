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
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import com.powsybl.network.conversion.server.dto.EquipmentType;
import com.powsybl.network.conversion.server.elasticsearch.EquipmentInfosService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.iidm.impl.NetworkImpl;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"spring.data.elasticsearch.enabled=true"})
public class EquipmentInfosServiceTests {

    private static final String TEST_FILE = "testCase.xiidm";

    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @Test
    public void testAddDeleteEquipmentInfos() {
        EqualsVerifier.simple().forClass(EquipmentInfos.class).verify();

        List<EquipmentInfos> infos = List.of(
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type(EquipmentType.LOAD.name()).voltageLevelsIds(Set.of("vl1")).build(),
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id2").name("name2").type(EquipmentType.LOAD.name()).voltageLevelsIds(Set.of("vl2")).build()
        );

        equipmentInfosService.addAll(infos);
        assertEquals(2, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.deleteAll(NETWORK_UUID);
        assertEquals(0, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));
    }

    @Test
    public void testEquipmentType() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);

        assertEquals(EquipmentType.SUBSTATION, EquipmentType.getType(network.getSubstation("BBE1AA")));
        assertEquals(EquipmentType.VOLTAGE_LEVEL, EquipmentType.getType(network.getVoltageLevel("BBE1AA1")));
        assertEquals(EquipmentType.GENERATOR, EquipmentType.getType(network.getGenerator("BBE1AA1 _generator")));
        assertEquals(EquipmentType.LOAD, EquipmentType.getType(network.getLoad("BBE1AA1 _load")));
        assertEquals(EquipmentType.LINE, EquipmentType.getType(network.getLine("BBE1AA1  BBE2AA1  1")));
        assertEquals(EquipmentType.TWO_WINDINGS_TRANSFORMER, EquipmentType.getType(network.getTwoWindingsTransformer("BBE1AA2  BBE3AA1  2")));
        assertEquals(EquipmentType.CONFIGURED_BUS, EquipmentType.getType(network.getBusBreakerView().getBus("BBE1AA1 ")));
    }

    @Test
    public void testVoltageLevelsIds() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);

        assertEquals(Set.of("BBE1AA1", "BBE1AA2"), EquipmentInfos.getVoltageLevelsIds(network.getSubstation("BBE1AA")));
        assertEquals(Set.of("BBE1AA1"), EquipmentInfos.getVoltageLevelsIds(network.getVoltageLevel("BBE1AA1")));
        assertEquals(Set.of("BBE1AA1"), EquipmentInfos.getVoltageLevelsIds(network.getGenerator("BBE1AA1 _generator")));
        assertEquals(Set.of("BBE1AA1"), EquipmentInfos.getVoltageLevelsIds(network.getLoad("BBE1AA1 _load")));
        assertEquals(Set.of("BBE1AA1", "BBE2AA1"), EquipmentInfos.getVoltageLevelsIds(network.getLine("BBE1AA1  BBE2AA1  1")));
        assertEquals(Set.of("BBE1AA1", "BBE1AA2"), EquipmentInfos.getVoltageLevelsIds(network.getTwoWindingsTransformer("BBE1AA2  BBE3AA1  2")));
        assertEquals(Set.of("BBE1AA1"), EquipmentInfos.getVoltageLevelsIds(network.getBusBreakerView().getBus("BBE1AA1 ")));
    }

    @Test
    public void testBadEquipmentType() {
        Identifiable<Network> network = new NetworkFactoryImpl().createNetwork("test", "test");
        String errorMessage = assertThrows(NetworkConversionException.class, () -> EquipmentType.getType(network)).getMessage();
        assertTrue(errorMessage.contains(String.format("The equipment type : %s is unknown", NetworkImpl.class.getSimpleName())));

        errorMessage = assertThrows(NetworkConversionException.class, () -> EquipmentInfos.getVoltageLevelsIds(network)).getMessage();
        assertTrue(errorMessage.contains(String.format("The equipment type : %s is unknown", NetworkImpl.class.getSimpleName())));
    }
}
