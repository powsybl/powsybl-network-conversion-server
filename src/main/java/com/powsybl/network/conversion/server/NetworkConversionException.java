/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import java.util.Objects;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
public final class NetworkConversionException extends RuntimeException {

    public enum Type {
        UNSUPPORTED_FORMAT
    }

    private final Type type;

    private NetworkConversionException(Type type, String msg) {
        super(msg);
        this.type = Objects.requireNonNull(type);
    }

    public Type getType() {
        return type;
    }

    public static NetworkConversionException createFormatUnsupported(String format) {
        Objects.requireNonNull(format);
        return new NetworkConversionException(Type.UNSUPPORTED_FORMAT, "The format: " + format + " is unsupported");
    }

}
