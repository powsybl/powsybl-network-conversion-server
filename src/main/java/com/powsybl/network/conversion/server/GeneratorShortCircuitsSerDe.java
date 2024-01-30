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
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuit;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;

/**
 * @author Miora Vedelago <miora.ralambotiana at rte-france.com>
 */
@AutoService(ExtensionSerDe.class)
public class GeneratorShortCircuitsSerDe extends AbstractExtensionSerDe<Generator, GeneratorShortCircuit> {

    public GeneratorShortCircuitsSerDe() {
        super("generatorShortCircuits", "network", GeneratorShortCircuit.class, "generatorShortCircuits.xsd", "http://www.itesla_project.eu/schema/iidm/ext/generator_short_circuits/1_0",
                "gsc");
    }

    @Override
    public void write(GeneratorShortCircuit extension, SerializerContext serializerContext) {
        throw new IllegalStateException("Should not happen");
    }

    @Override
    public GeneratorShortCircuit read(Generator extendable, DeserializerContext deserializerContext) {
        TreeDataReader reader = deserializerContext.getReader();
        float transientReactance = reader.readFloatAttribute("transientReactance");
        float stepUpTransformerReactance = reader.readFloatAttribute("stepUpTransformerReactance");
        reader.readEndNode();
        return extendable.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectTransX(transientReactance)
                .withStepUpTransformerX(stepUpTransformerReactance)
                .add();
    }
}
