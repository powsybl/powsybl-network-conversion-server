/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionXmlSerializer;
import com.powsybl.commons.xml.XmlReaderContext;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.commons.xml.XmlWriterContext;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;

/**
 * HACK!!! this class should be included in iidm-cvg-extensions. To remove when migration to rte-core 3.10.0.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(ExtensionXmlSerializer.class)
public class GeneratorActivePowerControlXmlSerializer<T extends Injection<T>> implements ExtensionXmlSerializer<T, ActivePowerControl<T>> {

    @Override
    public String getExtensionName() {
        return "generatorActivePowerControl";
    }

    @Override
    public String getCategoryName() {
        return "network";
    }

    @Override
    public Class<? super ActivePowerControl<T>> getExtensionClass() {
        return ActivePowerControl.class;
    }

    @Override
    public boolean hasSubElements() {
        return false;
    }

    @Override
    public InputStream getXsdAsStream() {
        return getClass().getResourceAsStream("/xsd/generatorActivePowerControl.xsd");
    }

    @Override
    public String getNamespaceUri() {
        return "http://www.itesla_project.eu/schema/iidm/ext/generator_active_power_control/1_0";
    }

    @Override
    public String getNamespacePrefix() {
        return "gapc";
    }

    @Override
    public void write(ActivePowerControl activePowerControl, XmlWriterContext context) throws XMLStreamException {
        context.getWriter().writeAttribute("participate", Boolean.toString(activePowerControl.isParticipate()));
        XmlUtil.writeFloat("droop", activePowerControl.getDroop(), context.getWriter());
    }

    @Override
    public ActivePowerControl<T> read(T identifiable, XmlReaderContext context) {
        boolean participate = XmlUtil.readBoolAttribute(context.getReader(), "participate");
        float droop = XmlUtil.readFloatAttribute(context.getReader(), "droop");
        identifiable.newExtension(ActivePowerControlAdder.class)
                .withParticipate(participate)
                .withDroop(droop)
                .add();
        return identifiable.getExtension(ActivePowerControl.class);
    }
}
