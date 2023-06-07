/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.AbstractExtensionXmlSerializer;
import com.powsybl.commons.extensions.ExtensionXmlSerializer;
import com.powsybl.commons.xml.XmlReaderContext;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.commons.xml.XmlWriterContext;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuit;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuitAdder;

/**
 * @author Miora Vedelago <miora.ralambotiana at rte-france.com>
 */
@AutoService(ExtensionXmlSerializer.class)
public class VoltageLevelShortCircuitsXmlSerializer extends AbstractExtensionXmlSerializer<VoltageLevel, IdentifiableShortCircuit<VoltageLevel>> {

    public VoltageLevelShortCircuitsXmlSerializer() {
        super("voltageLevelShortCircuits", "network", IdentifiableShortCircuit.class,
                false, "voltageLevelShortCircuits.xsd", "http://www.itesla_project.eu/schema/iidm/ext/voltagelevel_short_circuits/1_0",
                "vlsc");
    }

    @Override
    public void write(IdentifiableShortCircuit<VoltageLevel> extension, XmlWriterContext context) {
        throw new IllegalStateException("Should not happen");
    }

    @Override
    public IdentifiableShortCircuit<VoltageLevel> read(VoltageLevel extendable, XmlReaderContext context) {
        float minShortCircuitsCurrent = XmlUtil.readFloatAttribute(context.getReader(), "minShortCircuitsCurrent");
        float maxShortCircuitsCurrent = XmlUtil.readFloatAttribute(context.getReader(), "maxShortCircuitsCurrent");
        return (IdentifiableShortCircuit<VoltageLevel>) extendable.newExtension(IdentifiableShortCircuitAdder.class)
                .withIpMin(minShortCircuitsCurrent)
                .withIpMax(maxShortCircuitsCurrent)
                .add();
    }
}
