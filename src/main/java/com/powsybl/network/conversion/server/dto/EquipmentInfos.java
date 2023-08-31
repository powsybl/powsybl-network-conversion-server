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

    public static Set<VoltageLevelInfos> getVoltageLevels(@NonNull Identifiable<?> identifiable) {
        if (identifiable instanceof Substation) {
            return ((Substation) identifiable).getVoltageLevelStream().map(vl -> VoltageLevelInfos.builder().id(vl.getId()).name(vl.getNameOrId()).build()).collect(Collectors.toSet());
        } else if (identifiable instanceof VoltageLevel) {
            return Set.of(VoltageLevelInfos.builder().id(identifiable.getId()).name(identifiable.getNameOrId()).build());
        } else if (identifiable instanceof Switch) {
            return Set.of(VoltageLevelInfos.builder().id(((Switch) identifiable).getVoltageLevel().getId()).name(((Switch) identifiable).getVoltageLevel().getNameOrId()).build());
        } else if (identifiable instanceof Injection) {
            return Set.of(VoltageLevelInfos.builder().id(((Injection<?>) identifiable).getTerminal().getVoltageLevel().getId()).name(((Injection<?>) identifiable).getTerminal().getVoltageLevel().getNameOrId()).build());
        } else if (identifiable instanceof Bus) {
            return Set.of(VoltageLevelInfos.builder().id(((Bus) identifiable).getVoltageLevel().getId()).name(((Bus) identifiable).getVoltageLevel().getNameOrId()).build());
        } else if (identifiable instanceof HvdcLine) {
            HvdcLine hvdcLine = (HvdcLine) identifiable;
            return Set.of(
                VoltageLevelInfos.builder().id(hvdcLine.getConverterStation1().getTerminal().getVoltageLevel().getId()).name(hvdcLine.getConverterStation1().getTerminal().getVoltageLevel().getNameOrId()).build(),
                VoltageLevelInfos.builder().id(hvdcLine.getConverterStation2().getTerminal().getVoltageLevel().getId()).name(hvdcLine.getConverterStation2().getTerminal().getVoltageLevel().getNameOrId()).build()
            );
        } else if (identifiable instanceof Branch) {
            Branch<?> branch = (Branch<?>) identifiable;
            return Stream.of(
                     VoltageLevelInfos.builder().id(branch.getTerminal1().getVoltageLevel().getId()).name(branch.getTerminal1().getVoltageLevel().getNameOrId()).build(),
                     VoltageLevelInfos.builder().id(branch.getTerminal2().getVoltageLevel().getId()).name(branch.getTerminal2().getVoltageLevel().getNameOrId()).build()
                )
                .collect(Collectors.toSet());
        } else if (identifiable instanceof ThreeWindingsTransformer) {
            ThreeWindingsTransformer w3t = (ThreeWindingsTransformer) identifiable;
            return Stream.of(
                    VoltageLevelInfos.builder().id(w3t.getTerminal(ThreeWindingsTransformer.Side.ONE).getVoltageLevel().getId()).name(w3t.getTerminal(ThreeWindingsTransformer.Side.ONE).getVoltageLevel().getNameOrId()).build(),
                    VoltageLevelInfos.builder().id(w3t.getTerminal(ThreeWindingsTransformer.Side.TWO).getVoltageLevel().getId()).name(w3t.getTerminal(ThreeWindingsTransformer.Side.TWO).getVoltageLevel().getNameOrId()).build(),
                    VoltageLevelInfos.builder().id(w3t.getTerminal(ThreeWindingsTransformer.Side.THREE).getVoltageLevel().getId()).name(w3t.getTerminal(ThreeWindingsTransformer.Side.THREE).getVoltageLevel().getNameOrId()).build()
                )
                .collect(Collectors.toSet());
        }

        throw NetworkConversionException.createEquipmentTypeUnknown(identifiable.getClass().getSimpleName());
    }

    public static Set<SubstationInfos> getSubstations(@NonNull Identifiable<?> identifiable) {
        if (identifiable instanceof Substation) {
            Substation substation = (Substation) identifiable;
            return Set.of(SubstationInfos.builder().id(substation.getId()).name(substation.getNameOrId()).build());
        } else if (identifiable instanceof VoltageLevel) {
            VoltageLevel voltageLevel = (VoltageLevel) identifiable;
            return Set.of(SubstationInfos.builder().id(voltageLevel.getSubstation().map(Substation::getId).orElse(null)).name(voltageLevel.getSubstation().map(Substation::getNameOrId).orElse(null)).build());
        } else if (identifiable instanceof Switch) {
            Switch switchEquipment = (Switch) identifiable;
            return Set.of(SubstationInfos.builder().id(switchEquipment.getVoltageLevel().getSubstation().map(Substation::getId).orElse(null)).name(switchEquipment.getVoltageLevel().getSubstation().map(Substation::getNameOrId).orElse(null)).build());
        } else if (identifiable instanceof Injection<?>) {
            Injection<?> injection = (Injection<?>) identifiable;
            return Set.of(SubstationInfos.builder().id(injection.getTerminal().getVoltageLevel().getSubstation().map(Substation::getId).orElse(null)).name(injection.getTerminal().getVoltageLevel().getSubstation().map(Substation::getNameOrId).orElse(null)).build());
        } else if (identifiable instanceof Bus) {
            Bus bus = (Bus) identifiable;
            return Set.of(SubstationInfos.builder().id(bus.getVoltageLevel().getSubstation().map(Substation::getId).orElse(null)).name(bus.getVoltageLevel().getSubstation().map(Substation::getNameOrId).orElse(null)).build());
        } else if (identifiable instanceof HvdcLine) {
            HvdcLine hvdcLine = (HvdcLine) identifiable;
            Substation substation1 = hvdcLine.getConverterStation1().getTerminal().getVoltageLevel().getSubstation().orElse(null);
            Substation substation2 = hvdcLine.getConverterStation2().getTerminal().getVoltageLevel().getSubstation().orElse(null);
            return Stream.of(
                            SubstationInfos.builder().id(substation1 != null ? substation1.getId() : null).name(substation1 != null ? substation1.getNameOrId() : null).build(),
                            SubstationInfos.builder().id(substation2 != null ? substation2.getId() : null).name(substation2 != null ? substation2.getNameOrId() : null).build()
                    )
                    .collect(Collectors.toSet());
        } else if (identifiable instanceof Branch<?>) {
            Branch<?> branch = (Branch<?>) identifiable;
            Substation substation1 = branch.getTerminal1().getVoltageLevel().getSubstation().orElse(null);
            Substation substation2 = branch.getTerminal2().getVoltageLevel().getSubstation().orElse(null);
            return Stream.of(
                            SubstationInfos.builder().id(substation1 != null ? substation1.getId() : null).name(substation1 != null ? substation1.getNameOrId() : null).build(),
                            SubstationInfos.builder().id(substation2 != null ? substation2.getId() : null).name(substation2 != null ? substation2.getNameOrId() : null).build()
                    )
                    .collect(Collectors.toSet());
        } else if (identifiable instanceof ThreeWindingsTransformer) {
            ThreeWindingsTransformer w3t = (ThreeWindingsTransformer) identifiable;
            Substation substation1 = w3t.getTerminal(ThreeWindingsTransformer.Side.ONE).getVoltageLevel().getSubstation().orElse(null);
            Substation substation2 = w3t.getTerminal(ThreeWindingsTransformer.Side.TWO).getVoltageLevel().getSubstation().orElse(null);
            Substation substation3 = w3t.getTerminal(ThreeWindingsTransformer.Side.THREE).getVoltageLevel().getSubstation().orElse(null);
            return Stream.of(
                    SubstationInfos.builder().id(substation1 != null ? substation1.getId() : null).name(substation1 != null ? substation1.getNameOrId() : null).build(),
                    SubstationInfos.builder().id(substation2 != null ? substation2.getId() : null).name(substation2 != null ? substation2.getNameOrId() : null).build(),
                    SubstationInfos.builder().id(substation3 != null ? substation3.getId() : null).name(substation3 != null ? substation3.getNameOrId() : null).build()
            ).collect(Collectors.toSet());
        }
        throw NetworkConversionException.createEquipmentTypeUnknown(identifiable.getClass().getSimpleName());
    }
}
