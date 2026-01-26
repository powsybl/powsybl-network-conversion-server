/*
  Copyright (c) 2026, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */

@AllArgsConstructor
@Getter
public class ExportInfos {
    private String filename;

    private UUID exportUuid;

    private String format;

    private String receiver;

    private Map<String, Object> formatParameters;

    private String extraData;
}
