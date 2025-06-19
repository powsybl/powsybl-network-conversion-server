/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ExportNetworkInfos {

    private String networkName;

    private byte[] networkData;

    private Path tempFilePath;

    private long numberBuses;

    public ExportNetworkInfos(String networkName, Path tempFilePath, long numberBuses) {
        this.networkName = networkName;
        this.tempFilePath = tempFilePath;
        this.numberBuses = numberBuses;
    }

    public ExportNetworkInfos(String networkName, byte[] networkData, long numberBuses) {
        this.networkName = networkName;
        this.networkData = networkData;
        this.numberBuses = numberBuses;
    }
}
