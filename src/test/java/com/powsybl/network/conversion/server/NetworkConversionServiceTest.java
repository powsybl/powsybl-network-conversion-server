/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.network.conversion.server;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkConversionServiceTest {

    @Test
    void testGetMainFileNameWithExtensionFindsNameWithExtension() {
        assertEquals("network.unknown", NetworkConversionService.getMainFileNameWithExtension("network", Set.of("network.unknown", "network_data")).get());
    }

    @Test
    void testGetMainFileNameWithExtensionStaysEmptyIfNot() {
        assertTrue(NetworkConversionService.getMainFileNameWithExtension("network", Set.of("network_data", "other.xml")).isEmpty());
    }
}
