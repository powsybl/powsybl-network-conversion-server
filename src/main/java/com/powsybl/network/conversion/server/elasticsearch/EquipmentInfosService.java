/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.elasticsearch;

import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * An interface to define an api for metadatas transfer in the DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Service
public interface EquipmentInfosService {
    Iterable<EquipmentInfos> addAll(@NonNull final Iterable<EquipmentInfos> equipmentInfos);

    Iterable<EquipmentInfos> findAll(@NonNull UUID networkUuid);

    void deleteAll(@NonNull UUID networkUuid);
}
