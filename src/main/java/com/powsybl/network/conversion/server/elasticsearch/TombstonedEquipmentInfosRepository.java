/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.elasticsearch;

import com.powsybl.network.conversion.server.dto.TombstonedEquipmentInfos;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
public interface TombstonedEquipmentInfosRepository extends ElasticsearchRepository<TombstonedEquipmentInfos, String> {
    List<TombstonedEquipmentInfos> findAllByNetworkUuidAndVariantId(UUID networkUuid, String variantId);
}
