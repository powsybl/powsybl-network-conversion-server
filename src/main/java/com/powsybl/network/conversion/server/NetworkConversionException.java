package com.powsybl.network.conversion.server;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
class NetworkConversionException extends RuntimeException {

    NetworkConversionException(String msg) {
        super(msg);
    }

    NetworkConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}

