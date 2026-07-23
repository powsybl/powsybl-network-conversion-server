/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Getter
public final class NetworkConversionException extends RuntimeException {

    private NetworkConversionException(String msg) {
        super(msg);
    }

    private NetworkConversionException(Throwable cause) {
        super(cause);
    }

    private NetworkConversionException(String msg, Exception cause) {
        super(msg, cause);
    }

    // TODO check if wanted as business
    public static NetworkConversionException createUnsupportedFormat(String format) {
        Objects.requireNonNull(format);
        return new NetworkConversionException("The format: " + format + " is unsupported");
    }

    public static NetworkConversionException createEquipmentTypeUnknown(String type) {
        Objects.requireNonNull(type);
        return new NetworkConversionException("The equipment type : " + type + " is unknown");
    }

    public static NetworkConversionException createVariantIdUnknown(String variantId) {
        Objects.requireNonNull(variantId);
        return new NetworkConversionException("The variant Id : " + variantId + " is unknown");
    }

    public static NetworkConversionException createFailedNetworkSaving(UUID networkUuid, Exception cause) {
        return new NetworkConversionException(String.format("The save of network '%s' has failed", networkUuid), cause);
    }

    public static NetworkConversionException createFailedCaseImport(Throwable cause) {
        return new NetworkConversionException(cause);
    }

    public static NetworkConversionException createFailedCaseExport(Exception cause) {
        return new NetworkConversionException("Case export failed", cause);
    }

    public static NetworkConversionException failedToStreamNetworkToFile(Exception cause) {
        return new NetworkConversionException("Failed to stream network to file", cause);
    }

    // TODO check if wanted as business
    public static NetworkConversionException createHybridHvdcUnsupported(String hvdcId) {
        Objects.requireNonNull(hvdcId);
        return new NetworkConversionException(String.format("The hybrid Hvdc line %s is unsupported", hvdcId));
    }

    public static NetworkConversionException createFailedNetworkReindex(UUID networkUuid, Exception cause) {
        return new NetworkConversionException(String.format("Reindex of network '%s' has failed", networkUuid), cause);
    }

    public static NetworkConversionException createFailedDownloadExportFile(String exportUuid) {
        return new NetworkConversionException(String.format("Failed to download file for export UUID '%s'", exportUuid));
    }
}
