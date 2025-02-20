/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import org.springframework.http.HttpStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public final class NetworkConversionException extends RuntimeException {

    public enum Type {
        UNSUPPORTED_HYBRID_HVDC(HttpStatus.INTERNAL_SERVER_ERROR),
        UNSUPPORTED_FORMAT(HttpStatus.INTERNAL_SERVER_ERROR),
        UNKNOWN_EQUIPMENT_TYPE(HttpStatus.INTERNAL_SERVER_ERROR),
        UNKNOWN_VARIANT_ID(HttpStatus.NOT_FOUND),
        FAILED_NETWORK_SAVING(HttpStatus.INTERNAL_SERVER_ERROR),
        FAILED_CASE_IMPORT(HttpStatus.INTERNAL_SERVER_ERROR),
        FAILED_CASE_EXPORT(HttpStatus.INTERNAL_SERVER_ERROR);

        public final HttpStatus status;

        HttpStatus getStatus() {
            return status;
        }

        Type(HttpStatus status) {
            this.status = status;
        }
    }

    private final Type type;

    private NetworkConversionException(Type type, String msg) {
        super(msg);
        this.type = Objects.requireNonNull(type);
    }

    private NetworkConversionException(Type type, String msg, Exception cause) {
        super(msg, cause);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public static NetworkConversionException createFormatUnsupported(String format) {
        Objects.requireNonNull(format);
        return new NetworkConversionException(Type.UNSUPPORTED_FORMAT, "The format: " + format + " is unsupported");
    }

    public static NetworkConversionException createEquipmentTypeUnknown(String type) {
        Objects.requireNonNull(type);
        return new NetworkConversionException(Type.UNKNOWN_EQUIPMENT_TYPE, "The equipment type : " + type + " is unknown");
    }

    public static NetworkConversionException createVariantIdUnknown(String variantId) {
        Objects.requireNonNull(variantId);
        return new NetworkConversionException(Type.UNKNOWN_VARIANT_ID, "The variant Id : " + variantId + " is unknown");
    }

    public static NetworkConversionException createFailedNetworkSaving(UUID networkUuid, Exception cause) {
        return new NetworkConversionException(Type.FAILED_NETWORK_SAVING, String.format("The save of network '%s' has failed", networkUuid), cause);
    }

    public static NetworkConversionException createFailedCaseImport(Exception cause) {
        return new NetworkConversionException(Type.FAILED_CASE_IMPORT, "Case import failed", cause);
    }

    public static NetworkConversionException createFailedCaseExport(Exception cause) {
        return new NetworkConversionException(Type.FAILED_CASE_EXPORT, "Case export failed", cause);
    }
  
    public static NetworkConversionException createHybridHvdcUnsupported(String hvdcId) {
        Objects.requireNonNull(hvdcId);
        return new NetworkConversionException(Type.UNSUPPORTED_HYBRID_HVDC, String.format("The hybrid Hvdc line %s is unsupported", hvdcId));
    }  
}
