package com.powsybl.networkconverterserver;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
public class NetworkConverterException extends RuntimeException {

    public NetworkConverterException() {
    }

    public NetworkConverterException(String msg) {
        super(msg);
    }

    public NetworkConverterException(Throwable throwable) {
        super(throwable);
    }

    public NetworkConverterException(String message, Throwable cause) {
        super(message, cause);
    }
}

