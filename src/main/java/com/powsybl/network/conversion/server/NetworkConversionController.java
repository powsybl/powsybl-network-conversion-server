/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.network.conversion.server.dto.BoundaryInfos;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import com.powsybl.network.conversion.server.dto.NetworkInfos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + NetworkConversionConstants.API_VERSION + "/")
@Tag(name = "network-converter-server")
@ComponentScan(basePackageClasses = NetworkConversionService.class)
public class NetworkConversionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionController.class);

    @Autowired
    private NetworkConversionService networkConversionService;

    @PostMapping(value = "/networks")
    @Operation(summary = "Get a case file from its name and import it into the store")
    public ResponseEntity<NetworkInfos> importCase(@Parameter(description = "Case UUID") @RequestParam("caseUuid") UUID caseUuid,
                                                   @Parameter(description = "Variant ID") @RequestParam(name = "variantId", required = false) String variantId) {
        LOGGER.debug("Importing case {}...", caseUuid);
        NetworkInfos networkInfos = networkConversionService.importCase(caseUuid, variantId);
        return ResponseEntity.ok().body(networkInfos);
    }

    @GetMapping(value = "/networks/{networkUuid}/export/{format}")
    @Operation(summary = "Export a network from the network-store")
    public ResponseEntity<byte[]> exportNetwork(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                @Parameter(description = "Export format")@PathVariable("format") String format,
                                                @Parameter(description = "Other networks UUID") @RequestParam(name = "networkUuid", required = false) List<String> otherNetworks) throws IOException {
        LOGGER.debug("Exporting network {}...", networkUuid);

        List<UUID> otherNetworksUuid = otherNetworks != null ? otherNetworks.stream().map(UUID::fromString).collect(Collectors.toList()) : Collections.emptyList();

        ExportNetworkInfos exportNetworkInfos = networkConversionService.exportNetwork(networkUuid, otherNetworksUuid, format);
        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportNetworkInfos.getNetworkName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportNetworkInfos.getNetworkData());
    }

    @GetMapping(value = "/export/formats")
    @Operation(summary = "Get a list of the available format")
    public ResponseEntity<Collection<String>> getAvailableFormat() {
        LOGGER.debug("GetAvailableFormat ...");
        Collection<String> formats = networkConversionService.getAvailableFormat();
        return ResponseEntity.ok().body(formats);

    }

    @GetMapping(value = "/networks/{networkUuid}/export-sv-cgmes")
    @Operation(summary = "Export a merged cgmes network from the network-store")
    public ResponseEntity<byte[]> exportCgmesSv(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                @Parameter(description = "Other networks UUID") @RequestParam(name = "networkUuid", required = false) List<String> otherNetworks) throws XMLStreamException {
        LOGGER.debug("Exporting network {}...", networkUuid);
        List<UUID> otherNetworksUuid = otherNetworks != null ? otherNetworks.stream().map(UUID::fromString).collect(Collectors.toList()) : Collections.emptyList();
        ExportNetworkInfos exportNetworkInfos = networkConversionService.exportCgmesSv(networkUuid, otherNetworksUuid);
        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportNetworkInfos.getNetworkName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportNetworkInfos.getNetworkData());
    }

    @PostMapping(value = "/networks/cgmes")
    @Operation(summary = "Import a cgmes case into the store, using provided boundaries")
    public ResponseEntity<NetworkInfos> importCgmesCase(@RequestParam("caseUuid") UUID caseUuid,
                                                        @RequestBody(required = false) List<BoundaryInfos> boundaries) {
        LOGGER.debug("Importing cgmes case {}...", caseUuid);
        var networkInfos = networkConversionService.importCgmesCase(caseUuid, boundaries);
        return ResponseEntity.ok().body(networkInfos);
    }
}
