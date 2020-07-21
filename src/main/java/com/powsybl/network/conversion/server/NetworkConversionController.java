/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import com.powsybl.network.conversion.server.dto.NetworkInfos;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + NetworkConversionConstants.API_VERSION + "/")
@Api(tags = "network-converter-server")
@ComponentScan(basePackageClasses = NetworkConversionService.class)
public class NetworkConversionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionController.class);

    @Autowired
    private NetworkConversionService networkConversionService;

    @PostMapping(value = "/networks")
    @ApiOperation(value = "Get a case file from its name and import it into the store")
    public ResponseEntity<NetworkInfos> importCase(@RequestParam("caseUuid") UUID caseUuid) {
        LOGGER.debug("Importing case {}...", caseUuid);
        NetworkInfos networkInfos = networkConversionService.importCase(caseUuid);
        return ResponseEntity.ok().body(networkInfos);
    }

    @GetMapping(value = "/networks/{networkUuid}/export/{format}")
    @ApiOperation(value = "Export a network from the network-store")
    public ResponseEntity<byte[]> exportNetwork(@PathVariable("networkUuid") UUID networkUuid, @PathVariable("format") String format) throws IOException {
        LOGGER.debug("Exporting network {}...", networkUuid);
        ExportNetworkInfos exportNetworkInfos = networkConversionService.exportCase(networkUuid, format);
        byte[] networkData = new ObjectMapper().writeValueAsBytes(exportNetworkInfos);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(networkData);
    }

    @GetMapping(value = "/export/formats")
    @ApiOperation(value = "Get a list of the available format")
    public ResponseEntity<Collection<String>> getAvailableFormat() {
        LOGGER.debug("GetAvailableFormat ...");
        Collection<String> formats = networkConversionService.getAvailableFormat();
        return ResponseEntity.ok().body(formats);

    }
}
