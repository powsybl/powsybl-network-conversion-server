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
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import com.powsybl.network.conversion.server.dto.VoltageLevelInfos;
import com.powsybl.network.conversion.server.elasticsearch.EquipmentInfosService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.iidm.impl.NetworkImpl;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EquipmentInfosServiceTests {

    private static final String TEST_FILE = "testCase.xiidm";

    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @Before
    public void setup() {
        equipmentInfosService.deleteAll(NETWORK_UUID);
    }

    @Test
    public void testAddDeleteEquipmentInfos() {
        EqualsVerifier.simple().forClass(EquipmentInfos.class).verify();
        EqualsVerifier.simple().forClass(VoltageLevelInfos.class).verify();

        List<EquipmentInfos> infos = List.of(
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type(IdentifiableType.LOAD.name()).voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build(),
            EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id2").name("name2").type(IdentifiableType.LOAD.name()).voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build()
        );

        equipmentInfosService.addAll(infos);
        assertEquals(2, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.deleteAll(NETWORK_UUID);
        assertEquals(0, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));
    }

    @Test
    public void testVoltageLevels() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);

        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA2").name("BBE1AA2").build(), VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevels(network.getSubstation("BBE1AA")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevels(network.getVoltageLevel("BBE1AA1")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevels(network.getGenerator("BBE1AA1 _generator")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevels(network.getLoad("BBE1AA1 _load")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build(), VoltageLevelInfos.builder().id("BBE2AA1").name("BBE2AA1").build()), EquipmentInfos.getVoltageLevels(network.getLine("BBE1AA1  BBE2AA1  1")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA2").name("BBE1AA2").build(), VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevels(network.getTwoWindingsTransformer("BBE1AA2  BBE3AA1  2")));
        assertEquals(Set.of(VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").build()), EquipmentInfos.getVoltageLevels(network.getBusBreakerView().getBus("BBE1AA1 ")));
    }

    @Test
    public void testBadEquipmentType() {
        Identifiable<Network> network = new NetworkFactoryImpl().createNetwork("test", "test");

        String errorMessage = assertThrows(NetworkConversionException.class, () -> EquipmentInfos.getVoltageLevels(network)).getMessage();
        assertTrue(errorMessage.contains(String.format("The equipment type : %s is unknown", NetworkImpl.class.getSimpleName())));
    }
}
