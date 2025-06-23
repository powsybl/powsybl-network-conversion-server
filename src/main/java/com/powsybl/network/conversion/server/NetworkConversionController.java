/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.network.conversion.server.dto.BoundaryInfos;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import com.powsybl.network.conversion.server.dto.ImportExportFormatMeta;
import com.powsybl.network.conversion.server.dto.NetworkInfos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.xml.stream.XMLStreamException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    @Autowired
    private NetworkConversionObserver networkConversionObserver;

    @PostMapping(value = "/networks")
    @Operation(summary = "Get a case file from its name and import it into the store")
    public ResponseEntity<NetworkInfos> importCase(@Parameter(description = "Case UUID") @RequestParam("caseUuid") UUID caseUuid,
                                                   @Parameter(description = "Case format") @RequestParam(name = "caseFormat") String caseFormat,
                                                   @Parameter(description = "Variant ID") @RequestParam(name = "variantId", required = false) String variantId,
                                                   @Parameter(description = "Report UUID") @RequestParam(value = "reportUuid", required = false) UUID reportUuid,
                                                   @Parameter(description = "Import parameters") @RequestBody(required = false) Map<String, Object> importParameters,
                                                   @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                                   @Parameter(description = "Is import running asynchronously ?") @RequestParam(name = "isAsyncRun", required = false, defaultValue = "true") boolean isAsyncRun) {
        LOGGER.debug("Importing case {} {}...", caseUuid, isAsyncRun ? "asynchronously" : "synchronously");
        Map<String, Object> nonNullImportParameters = importParameters == null ? new HashMap<>() : importParameters;
        if (!isAsyncRun) {
            NetworkInfos networkInfos = networkConversionService.importCase(caseUuid, variantId, reportUuid, caseFormat, nonNullImportParameters);
            return ResponseEntity.ok().body(networkInfos);
        }

        networkConversionService.importCaseAsynchronously(caseUuid, variantId, reportUuid, caseFormat, nonNullImportParameters, receiver);
        return ResponseEntity.ok().build();
    }

    // Swagger RequestBody interferes badly with Spring RequestBody where required is false.
    // Had to put Swagger RequestBody in @Operation part,
    // it let swagger offer input for body, even though schema part is ignored
    @PostMapping(value = "/networks/{mainNetworkUuid}/export/{format}")
    @Operation(summary = "Export a network from the network-store",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Parameters for chosen format",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Properties.class))
        )
    )
    public ResponseEntity<InputStreamResource> exportNetwork(@Parameter(description = "Network UUID") @PathVariable("mainNetworkUuid") UUID networkUuid,
                                                             @Parameter(description = "Export format")@PathVariable("format") String format,
                                                             @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                                             @Parameter(description = "File name") @RequestParam(name = "fileName", required = false) String fileName,
                                                             @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, Object> formatParameters
                                                             ) {
        LOGGER.debug("Exporting network {}...", networkUuid);
        ExportNetworkInfos exportNetworkInfos = networkConversionObserver.observeExport(
                format,
                () -> networkConversionService.exportNetwork(networkUuid, variantId, fileName, format, formatParameters)
        );

        return networkConversionService.createExportNetworkResponse(exportNetworkInfos);
    }

    @PostMapping(value = "/cases/{caseUuid}/convert/{format}")
    @Operation(summary = "Export a network from case server in asked format",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Parameters for chosen format",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Properties.class))
        )
    )
    public ResponseEntity<InputStreamResource> exportNetwork(@Parameter(description = "case UUID") @PathVariable("caseUuid") UUID caseUuid,
                                              @Parameter(description = "Export format")@PathVariable("format") String format,
                                              @Parameter(description = "File name") @RequestParam(name = "fileName", required = false) String fileName,
                                              @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, Object> formatParameters) {
        LOGGER.debug("Converting network {}...", caseUuid);
        Optional<ExportNetworkInfos> exportNetworkInfos = networkConversionService.exportCase(caseUuid, format, fileName, formatParameters);
        if (exportNetworkInfos.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return networkConversionService.createConvertNetworkResponse(exportNetworkInfos.get());
    }

    @GetMapping(value = "/export/formats")
    @Operation(summary = "Get a list of the available format")
    public ResponseEntity<Map<String, ImportExportFormatMeta>> getAvailableFormat() {
        LOGGER.debug("getAvailableExportFormat ...");
        Map<String, ImportExportFormatMeta> formats = networkConversionService.getAvailableFormat();
        return ResponseEntity.ok().body(formats);
    }

    @GetMapping(value = "/cases/{caseUuid}/import-parameters")
    @Operation(summary = "Get import parameters for a case")
    public ResponseEntity<ImportExportFormatMeta> getCaseImportParameters(@Parameter(description = "Case UUID") @PathVariable(name = "caseUuid") UUID caseUuid) {
        LOGGER.debug("getImportParametersOfFormat ...");
        ImportExportFormatMeta parameters = networkConversionService.getCaseImportParameters(caseUuid);
        return ResponseEntity.ok().body(parameters);
    }

    @GetMapping(value = "/networks/{networkUuid}/export-sv-cgmes")
    @Operation(summary = "Export a cgmes network from the network-store")
    public ResponseEntity<byte[]> exportCgmesSv(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid) throws XMLStreamException {
        LOGGER.debug("Exporting network {}...", networkUuid);
        ExportNetworkInfos exportNetworkInfos = networkConversionObserver.observeExport("CGMES", () -> networkConversionService.exportCgmesSv(networkUuid));
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

    @PostMapping(value = "/networks/{networkUuid}/reindex-all")
    @Operation(summary = "reindex all equipments in network")
    public ResponseEntity<Void> reindexAllEquipments(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid) {
        LOGGER.debug("Reindex all equipments in network");
        networkConversionService.reindexAllEquipments(networkUuid);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/networks/{networkUuid}/indexed-equipments", method = RequestMethod.HEAD)
    @Operation(summary = "Check if the given network contains indexed equipments")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The network is indexed"),
        @ApiResponse(responseCode = "204", description = "The network isn't indexed"),
        @ApiResponse(responseCode = "404", description = "The network doesn't exist"),
    })
    public ResponseEntity<Void> checkNetworkIndexation(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid) {
        if (!networkConversionService.doesNetworkExist(networkUuid)) {
            return ResponseEntity.notFound().build();
        }
        return networkConversionService.hasEquipmentInfos(networkUuid)
            ? ResponseEntity.ok().build()
            : ResponseEntity.noContent().build();

    }
}
