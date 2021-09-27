/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.elasticsearch;

import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import org.springframework.lang.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 *  A class to implement elasticsearch indexing
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class EquipmentInfosServiceMock implements EquipmentInfosService {

    @Override
    public void addAll(@NonNull final List<EquipmentInfos> equipmentInfos) {
        // Nothing to insert
    }

    @Override
    public Iterable<EquipmentInfos> findAll(@NonNull UUID networkUuid) {
        return Collections.emptyList();
    }

    @Override
    public void deleteAll(@NonNull UUID networkUuid) {
        // Nothing to delete
    }
}
