/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
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
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(ExtensionSerDe.class)
public class GeneratorActivePowerControlSerDe<T extends Injection<T>> extends AbstractExtensionSerDe<T, ActivePowerControl<T>> {

    public GeneratorActivePowerControlSerDe() {
        super("generatorActivePowerControl", "network", ActivePowerControl.class, "generatorActivePowerControl.xsd", "http://www.itesla_project.eu/schema/iidm/ext/generator_active_power_control/1_0", "gapc");
    }

    @Override
    public void write(ActivePowerControl activePowerControl, SerializerContext serializerContext) {
        throw new IllegalStateException("Should never be called");
    }

    @Override
    public ActivePowerControl<T> read(T identifiable, DeserializerContext deserializerContext) {
        TreeDataReader reader = deserializerContext.getReader();
        boolean participate = reader.readBooleanAttribute("participate");
        float droop = reader.readFloatAttribute("droop");
        reader.readEndNode();
        identifiable.newExtension(ActivePowerControlAdder.class)
                .withParticipate(participate)
                .withDroop(droop)
                .add();
        return identifiable.getExtension(ActivePowerControl.class);
    }
}
