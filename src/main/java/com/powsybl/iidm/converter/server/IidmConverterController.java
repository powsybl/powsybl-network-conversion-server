/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.converter.server;

import com.powsybl.iidm.converter.server.dto.NetworkIds;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;

import static com.powsybl.iidm.converter.server.IidmConverterConstants.IIDM_CONVERTER_API_VERSION;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + IIDM_CONVERTER_API_VERSION + "/")
@Api(tags = "iidm-converter-server")
public class IidmConverterController {

    private static final Logger LOGGER = LoggerFactory.getLogger(IidmConverterController.class);

    @Inject
    private IidmConverterService iidmConverterService;

    @PostMapping(value = "/persistent-store/{caseName}")
    @ApiOperation(value = "Get a case file and stores it in DB")
    public ResponseEntity<NetworkIds> persistantStore(@PathVariable("caseName") String caseName) {
        LOGGER.debug("persistentStore request received with parameter caseName = {}", caseName);
        NetworkIds networkIds = iidmConverterService.persistentStore(caseName);
        return ResponseEntity.ok().body(networkIds);
    }
}
