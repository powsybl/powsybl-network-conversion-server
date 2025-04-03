/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.elasticsearch;

import com.google.common.collect.Lists;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import com.powsybl.network.conversion.server.dto.TombstonedEquipmentInfos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * A class to implement elasticsearch indexing
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Service
public class EquipmentInfosService {

    @Value("${spring.data.elasticsearch.partition-size:10000}")
    private int partitionSize;

    private final EquipmentInfosRepository equipmentInfosRepository;

    private final TombstonedEquipmentInfosRepository tombstonedEquipmentInfosRepository;

    public EquipmentInfosService(EquipmentInfosRepository equipmentInfosRepository, TombstonedEquipmentInfosRepository tombstonedEquipmentInfosRepository) {
        this.equipmentInfosRepository = equipmentInfosRepository;
        this.tombstonedEquipmentInfosRepository = tombstonedEquipmentInfosRepository;
    }

    public void addAll(@NonNull final List<EquipmentInfos> equipmentsInfos) {
        Lists.partition(equipmentsInfos, partitionSize)
                .parallelStream()
                .forEach(equipmentInfosRepository::saveAll);
    }

    public void addAllTombstonedEquipmentInfos(@NonNull final List<TombstonedEquipmentInfos> tombstonedEquipmentInfos) {
        Lists.partition(tombstonedEquipmentInfos, partitionSize)
                .parallelStream()
                .forEach(tombstonedEquipmentInfosRepository::saveAll);
    }

    public List<EquipmentInfos> findAll(@NonNull UUID networkUuid) {
        return equipmentInfosRepository.findAllByNetworkUuid(networkUuid);
    }

    public List<EquipmentInfos> findAllByNetworkUuidAndVariantId(UUID networkUuid, String variantId) {
        return equipmentInfosRepository.findAllByNetworkUuidAndVariantId(networkUuid, variantId);
    }

    public List<TombstonedEquipmentInfos> findAllTombstonedByNetworkUuidAndVariantId(UUID networkUuid, String variantId) {
        return tombstonedEquipmentInfosRepository.findAllByNetworkUuidAndVariantId(networkUuid, variantId);
    }

    public long count(@NonNull UUID networkUuid) {
        return equipmentInfosRepository.countByNetworkUuid(networkUuid);
    }

    public void deleteAllOnInitialVariant(@NonNull UUID networkUuid) {
        equipmentInfosRepository.deleteAllByNetworkUuidAndVariantId(networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID);
    }

    public void deleteAllByNetworkUuid(@NonNull UUID networkUuid) {
        equipmentInfosRepository.deleteAllByNetworkUuid(networkUuid);
    }
}
