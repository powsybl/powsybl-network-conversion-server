/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.dto;

import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

public class NetworkInfos {

    private UUID networkUuid;

    private String networkId;

    public NetworkInfos(UUID networkUuid, String networkId) {
        this.networkUuid = networkUuid;
        this.networkId = networkId;
    }

    public UUID getNetworkUuid() {
        return networkUuid;
    }

    public String getNetworkId() {
        return networkId;
    }
}
