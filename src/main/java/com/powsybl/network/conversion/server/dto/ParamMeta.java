/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.dto;

import com.powsybl.iidm.parameters.ParameterType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Class to convey an export format setting parameter description
 * @author Laurent Garnier <laurent.garnier@rte-france.com>
 */
@Getter
@AllArgsConstructor
public class ParamMeta {

    private final String name;

    private final ParameterType type;

    private final String description;

    private final Object defaultValue;
}
