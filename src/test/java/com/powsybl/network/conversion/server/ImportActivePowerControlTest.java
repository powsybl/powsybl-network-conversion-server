/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ImportActivePowerControlTest {

    @Test
    public void test() {
        var network = Importers.loadNetwork(new ResourceDataSource("testCaseOldActivePowerControl", new ResourceSet("/", "testCaseOldActivePowerControl.xiidm")));
        assertNotNull(network);
        var g1 = network.getGenerator("BBE1AA1 _generator");
        assertNotNull(g1);
        var e1 = g1.getExtension(ActivePowerControl.class);
        assertNotNull(e1);
        assertEquals(0, e1.getDroop(), 0);
        assertTrue(e1.isParticipate());
        var g3 = network.getGenerator("BBE3AA1 _generator");
        assertNotNull(g3);
        var e3 = g3.getExtension(ActivePowerControl.class);
        assertNotNull(e3);
        assertEquals(4, e3.getDroop(), 0);
        assertTrue(e3.isParticipate());
    }
}
