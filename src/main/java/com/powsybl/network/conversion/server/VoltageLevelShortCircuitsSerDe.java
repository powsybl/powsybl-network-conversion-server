/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.AbstractExtensionSerDe;
import com.powsybl.commons.extensions.ExtensionSerDe;
import com.powsybl.commons.io.DeserializerContext;
import com.powsybl.commons.io.SerializerContext;
import com.powsybl.commons.io.TreeDataReader;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuit;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuitAdder;

/**
 * @author Miora Vedelago <miora.ralambotiana at rte-france.com>
 */
@AutoService(ExtensionSerDe.class)
public class VoltageLevelShortCircuitsSerDe extends AbstractExtensionSerDe<VoltageLevel, IdentifiableShortCircuit<VoltageLevel>> {

    public VoltageLevelShortCircuitsSerDe() {
        super("voltageLevelShortCircuits", "network", IdentifiableShortCircuit.class,
                "voltageLevelShortCircuits.xsd", "http://www.itesla_project.eu/schema/iidm/ext/voltagelevel_short_circuits/1_0",
                "vlsc");
    }

    @Override
    public void write(IdentifiableShortCircuit<VoltageLevel> extension, SerializerContext serializerContext) {
        throw new IllegalStateException("Should not happen");
    }

    @Override
    public IdentifiableShortCircuit<VoltageLevel> read(VoltageLevel extendable, DeserializerContext deserializerContext) {
        TreeDataReader reader = deserializerContext.getReader();
        float minShortCircuitsCurrent = reader.readFloatAttribute("minShortCircuitsCurrent");
        float maxShortCircuitsCurrent = reader.readFloatAttribute("maxShortCircuitsCurrent");
        reader.readEndNode();
        return (IdentifiableShortCircuit<VoltageLevel>) extendable.newExtension(IdentifiableShortCircuitAdder.class)
                .withIpMin(minShortCircuitsCurrent)
                .withIpMax(maxShortCircuitsCurrent)
                .add();
    }
}
