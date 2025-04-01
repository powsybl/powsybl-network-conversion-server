/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class BasicEquipmentInfos {
    @Id
    @AccessType(AccessType.Type.PROPERTY)
    @SuppressWarnings("unused")
    public String getUniqueId() {
        return networkUuid + "_" + variantId + "_" + id;
    }

    @SuppressWarnings("unused")
    public void setUniqueId(String uniqueId) {
        // No setter because it a composite value
    }

    @MultiField(
        mainField = @Field(name = "equipmentId", type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "fullascii", type = FieldType.Keyword, normalizer = "fullascii"),
            @InnerField(suffix = "raw", type = FieldType.Keyword)
        }
    )
    private String id;

    private UUID networkUuid;

    private String variantId;

    public BasicEquipmentInfos(BasicEquipmentInfos other) {
        this.id = other.id;
        this.networkUuid = other.networkUuid;
        this.variantId = other.variantId;
    }
}
