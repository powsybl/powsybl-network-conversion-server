/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.elasticsearch;

import com.powsybl.network.conversion.server.dto.EquipmentInfos;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.lang.NonNull;

import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public interface EquipmentInfosRepository extends ElasticsearchRepository<EquipmentInfos, String> {
    Iterable<EquipmentInfos> findAllByNetworkUuid(@NonNull UUID networkUuid);

    long countByNetworkUuid(@NonNull UUID networkUuid);

    void deleteAllByNetworkUuid(UUID networkUuid);
}
