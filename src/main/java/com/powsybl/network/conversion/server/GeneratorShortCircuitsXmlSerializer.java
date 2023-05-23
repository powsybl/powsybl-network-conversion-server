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
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuit;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;

/**
 * @author Miora Vedelago <miora.ralambotiana at rte-france.com>
 */
@AutoService(ExtensionXmlSerializer.class)
public class GeneratorShortCircuitsXmlSerializer extends AbstractExtensionXmlSerializer<Generator, GeneratorShortCircuit> {

    public GeneratorShortCircuitsXmlSerializer() {
        super("generatorShortCircuits", "network", GeneratorShortCircuit.class, false, "generatorShortCircuits.xsd", "http://www.itesla_project.eu/schema/iidm/ext/generator_short_circuits/1_0",
                "gsc");
    }

    @Override
    public void write(GeneratorShortCircuit extension, XmlWriterContext context) {
        throw new IllegalStateException("Should not happen");
    }

    @Override
    public GeneratorShortCircuit read(Generator extendable, XmlReaderContext context) {
        float transientReactance = XmlUtil.readFloatAttribute(context.getReader(), "transientReactance");
        float stepUpTransformerReactance = XmlUtil.readFloatAttribute(context.getReader(), "stepUpTransformerReactance");
        return extendable.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectTransX(transientReactance)
                .withStepUpTransformerX(stepUpTransformerReactance)
                .add();
    }
}
