/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import com.powsybl.network.conversion.server.dto.NetworkInfos;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public ResponseEntity<byte[]> exportNetwork(@ApiParam(value = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                @ApiParam(value = "Export format")@PathVariable("format") String format,
                                                @ApiParam(value = "Other networks UUID") @RequestParam(name = "networkUuid", required = false) List<String> otherNetworks) throws IOException {
        LOGGER.debug("Exporting network {}...", networkUuid);

        List<UUID> otherNetworksUuid = otherNetworks != null ? otherNetworks.stream().map(UUID::fromString).collect(Collectors.toList()) : Collections.emptyList();

        ExportNetworkInfos exportNetworkInfos = networkConversionService.exportNetwork(networkUuid, otherNetworksUuid, format);
        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportNetworkInfos.getNetworkName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportNetworkInfos.getNetworkData());
    }

    @GetMapping(value = "/export/formats")
    @ApiOperation(value = "Get a list of the available format")
    public ResponseEntity<Collection<String>> getAvailableFormat() {
        LOGGER.debug("GetAvailableFormat ...");
        Collection<String> formats = networkConversionService.getAvailableFormat();
        return ResponseEntity.ok().body(formats);

    }

    @GetMapping(value = "/networks/{networkUuid}/export-sv-cgmes")
    @ApiOperation(value = "Export a merged cgmes network from the network-store")
    public ResponseEntity<byte[]> exportCgmesSv(@ApiParam(value = "Network UUID") @PathVariable("networkUuid") UUID networkUuid) throws IOException, XMLStreamException {
        LOGGER.debug("Exporting network {}...", networkUuid);
        ExportNetworkInfos exportNetworkInfos = networkConversionService.exportCgmesSv(networkUuid);
        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportNetworkInfos.getNetworkName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportNetworkInfos.getNetworkData());
    }

}
