/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class VoltageLevelInfos {

    String id;

    String name;

    public VoltageLevelInfos(VoltageLevelInfos other) {
        this.id = other.id;
        this.name = other.name;
    }
}
