/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.dto;

import com.powsybl.iidm.network.*;
import com.powsybl.network.conversion.server.NetworkConversionException;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Document(indexName = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}equipments")
@Setting(settingPath = "elasticsearch_settings.json")
@TypeAlias(value = "EquipmentInfos")
public class EquipmentInfos {
    @Id
    String uniqueId;

    @MultiField(
        mainField = @Field(name = "equipmentId", type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "fullascii", type = FieldType.Keyword, normalizer = "fullascii"),
            @InnerField(suffix = "raw", type = FieldType.Keyword)
        }
    )
    String id;

    @MultiField(
        mainField = @Field(name = "equipmentName", type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "fullascii", type = FieldType.Keyword, normalizer = "fullascii"),
            @InnerField(suffix = "raw", type = FieldType.Keyword)
        }
    )
    String name;

    @Field("equipmentType")
    String type;

    @Field(type = FieldType.Nested, includeInParent = true)
    Set<VoltageLevelInfos> voltageLevels;

    @Field(type = FieldType.Nested, includeInParent = true)
    private Set<SubstationInfos> substations;

    UUID networkUuid;

    String variantId;

    public static Set<VoltageLevel> getVoltageLevels(@NonNull Identifiable<?> identifiable) {
        if (identifiable instanceof Substation) {
            return ((Substation) identifiable).getVoltageLevelStream().collect(Collectors.toSet());
        } else if (identifiable instanceof VoltageLevel) {
            return Set.of((VoltageLevel) identifiable);
        } else if (identifiable instanceof Switch) {
            return Set.of(((Switch) identifiable).getVoltageLevel());
        } else if (identifiable instanceof Injection) {
            return Set.of(((Injection<?>) identifiable).getTerminal().getVoltageLevel());
        } else if (identifiable instanceof Bus) {
            return Set.of(((Bus) identifiable).getVoltageLevel());
        } else if (identifiable instanceof HvdcLine) {
            HvdcLine hvdcLine = (HvdcLine) identifiable;
            return Stream.of(
                    hvdcLine.getConverterStation1().getTerminal().getVoltageLevel(),
                    hvdcLine.getConverterStation2().getTerminal().getVoltageLevel()
            ).collect(Collectors.toSet());
        } else if (identifiable instanceof Branch) {
            Branch<?> branch = (Branch<?>) identifiable;
            return Stream.of(
                    branch.getTerminal1().getVoltageLevel(),
                    branch.getTerminal2().getVoltageLevel()
            ).collect(Collectors.toSet());
        } else if (identifiable instanceof ThreeWindingsTransformer) {
            ThreeWindingsTransformer w3t = (ThreeWindingsTransformer) identifiable;
            return Stream.of(
                    w3t.getTerminal(ThreeWindingsTransformer.Side.ONE).getVoltageLevel(),
                    w3t.getTerminal(ThreeWindingsTransformer.Side.TWO).getVoltageLevel(),
                    w3t.getTerminal(ThreeWindingsTransformer.Side.THREE).getVoltageLevel()
            ).collect(Collectors.toSet());
        }

        throw NetworkConversionException.createEquipmentTypeUnknown(identifiable.getClass().getSimpleName());
    }

    public static Set<VoltageLevelInfos> getVoltageLevelsInfos(@NonNull Identifiable<?> identifiable) {
        return getVoltageLevels(identifiable).stream()
                .map(vl -> VoltageLevelInfos.builder().id(vl.getId()).name(vl.getNameOrId()).build())
                .collect(Collectors.toSet());
    }

    public static Set<SubstationInfos> getSubstationsInfos(@NonNull Identifiable<?> identifiable) {
        return getVoltageLevels(identifiable).stream()
                .map(vl -> SubstationInfos.builder()
                        .id(vl.getSubstation().map(Substation::getId).orElse(null))
                        .name(vl.getSubstation().map(Substation::getNameOrId).orElse(null)).build())
                .collect(Collectors.toSet());
    }
}
