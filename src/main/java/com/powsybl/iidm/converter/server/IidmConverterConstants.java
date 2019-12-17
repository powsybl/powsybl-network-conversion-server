/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.converter.server;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

public final class IidmConverterConstants {

    private IidmConverterConstants() {
    }

    static final String IIDM_CONVERTER_API_VERSION = "v1";
    static final String CASE_API_VERSION = "v1";

    static final String DONE = "DONE";
    static final String ALREADY_EXISTS = "ALREADY_EXISTS";
    static final String DOESNT_EXISTS = "DOESN'T_EXISTS";
    static final String NOT_POSSIBLE = "NOT_POSSIBLE";

    static final String USERHOME = "user.home";
    static final String CASE_FOLDER = "/cases/";

}
