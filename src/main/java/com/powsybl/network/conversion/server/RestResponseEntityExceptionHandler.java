/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.network.conversion.server;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebInputException;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler {

    @ExceptionHandler(value = { NetworkConversionException.class, TypeMismatchException.class })
    protected ResponseEntity<Object> handleException(RuntimeException exception) {
        if (exception instanceof NetworkConversionException) {
            NetworkConversionException conversionException = (NetworkConversionException) exception;
            switch (conversionException.getType()) {
                case UNSUPPORTED_FORMAT:
                case UNKNOWN_EQUIPMENT_TYPE:
                case UNKNOWN_VARIANT_ID:
                case FAILED_NETWORK_SAVING:
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(conversionException.getMessage());
                default:
            }
        } else if (exception instanceof ServerWebInputException) {
            ServerWebInputException serverWebInputException = (ServerWebInputException) exception;
            Throwable cause = serverWebInputException.getCause();
            if (cause instanceof TypeMismatchException && cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
                return ResponseEntity.status(serverWebInputException.getStatus()).body(cause.getMessage());
            }
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
