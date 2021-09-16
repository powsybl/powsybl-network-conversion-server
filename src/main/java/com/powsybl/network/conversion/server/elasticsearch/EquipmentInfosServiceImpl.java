/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.elasticsearch;

import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import org.springframework.lang.NonNull;

import java.util.UUID;

/**
 * A class to implement elasticsearch indexing
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class EquipmentInfosServiceImpl implements EquipmentInfosService {

    private final EquipmentInfosRepository equipmentInfosRepository;

    public EquipmentInfosServiceImpl(EquipmentInfosRepository equipmentInfosRepository) {
        this.equipmentInfosRepository = equipmentInfosRepository;
    }

    @Override
    public Iterable<EquipmentInfos> addAll(@NonNull final Iterable<EquipmentInfos> equipmentInfos) {
        return equipmentInfosRepository.saveAll(equipmentInfos);
    }

    @Override
    public Iterable<EquipmentInfos> findAll(@NonNull UUID networkUuid) {
        return equipmentInfosRepository.findAllByNetworkUuid(networkUuid);
    }

    @Override
    public void deleteAll(@NonNull UUID networkUuid) {
        equipmentInfosRepository.deleteAllByNetworkUuid(networkUuid);
    }
}
