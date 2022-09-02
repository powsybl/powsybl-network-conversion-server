/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.network.conversion.server.dto;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Schema(description = "Case infos")
@TypeAlias(value = "CaseInfos")
public class CaseInfos {
    @Id
    @NonNull
    protected UUID uuid;
    @NonNull
    protected String name;
    @NonNull
    protected String format;

}
