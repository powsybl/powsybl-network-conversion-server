/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuit;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class ShortCircuitBackwardCompatibilityTest {
    @Test
    void test() {
        Network network = Network.read("sc-compat-test.xiidm", getClass().getResourceAsStream("/sc-compat-test.xiidm"));
        Generator gen = network.getGenerator("GEN");
        GeneratorShortCircuit genSc = gen.getExtension(GeneratorShortCircuit.class);
        assertNotNull(genSc);
        assertEquals(76.9000015258789, genSc.getDirectTransX(), 0);
        assertTrue(Double.isNaN(genSc.getDirectSubtransX()));
        assertEquals(30.299999237060547, genSc.getStepUpTransformerX(), 0);

        VoltageLevel vlgen = network.getVoltageLevel("VLGEN");
        IdentifiableShortCircuit vlgenSc = vlgen.getExtension(IdentifiableShortCircuit.class);
        assertNotNull(vlgenSc);
        assertEquals(10, vlgenSc.getIpMin(), 0);
        assertEquals(1000, vlgenSc.getIpMax(), 0);
    }
}
