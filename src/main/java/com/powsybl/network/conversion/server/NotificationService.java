/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import com.powsybl.network.conversion.server.dto.ExportInfos;
import com.powsybl.network.conversion.server.dto.NetworkInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(NotificationService.class);

    public static final String HEADER_VARIANT_ID = "variantId";
    public static final String HEADER_REPORT_UUID = "reportUuid";
    public static final String HEADER_NETWORK_ID = "networkId";
    public static final String HEADER_NETWORK_UUID = "networkUuid";
    public static final String HEADER_RECEIVER = "receiver";
    public static final String HEADER_EXPORT_INFOS = "exportInfos";
    public static final String HEADER_IMPORT_PARAMETERS = "importParameters";
    public static final String HEADER_CASE_FORMAT = "caseFormat";
    public static final String HEADER_CASE_NAME = "caseName";
    public static final String HEADER_EXPORT_PARAMETERS = "exportParameters";
    public static final String HEADER_FORMAT = "format";
    public static final String HEADER_FILE_NAME = "fileName";
    public static final String HEADER_USER_ID = "userId";
    public static final String HEADER_EXPORT_UUID = "exportUuid";
    public static final String HEADER_ERROR = "error";
    public static final String HEADER_S3_KEY = "s3Key";
    public static final String HEADER_EXPORT_CONTENT_TYPE = "exportContentType";

    @Autowired
    private StreamBridge networkConversionPublisher;

    private void sendCaseImportStartMessage(Message<UUID> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending import start message : {}", message);
        networkConversionPublisher.send("publishCaseImportStart-out-0", message);
    }

    private void sendCaseImportSucceededMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending import succeeded message : {}", message);
        networkConversionPublisher.send("publishCaseImportSucceeded-out-0", message);
    }

    private void sendNetworkExportStartMessage(Message<UUID> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending export network start message : {}", message);
        networkConversionPublisher.send("publishNetworkExportStart-out-0", message);
    }

    private void sendNetworkExportFinishedMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending export network finished message : {}", message);
        networkConversionPublisher.send("publishNetworkExportFinished-out-0", message);
    }

    private void sendCaseExportStartMessage(Message<UUID> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending export case start message : {}", message);
        networkConversionPublisher.send("publishCaseExportStart-out-0", message);
    }

    private void sendCaseExportFinishedMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending export case finished message : {}", message);
        networkConversionPublisher.send("publishCaseExportFinished-out-0", message);
    }

    public void emitCaseImportStart(UUID caseUuid, String variantId, UUID reportUuid, String caseFormat, Map<String, Object> importParameters, String receiver) {
        sendCaseImportStartMessage(MessageBuilder.withPayload(caseUuid)
                .setHeader(HEADER_VARIANT_ID, variantId)
                .setHeader(HEADER_REPORT_UUID, reportUuid != null ? reportUuid.toString() : null)
                .setHeader(HEADER_IMPORT_PARAMETERS, importParameters)
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_CASE_FORMAT, caseFormat)
                .build());
    }

    public void emitCaseImportSucceeded(NetworkInfos networkInfos, String caseNameStr, String caseFormatStr, String receiver, Map<String, String> importParameters) {
        sendCaseImportSucceededMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_NETWORK_ID, networkInfos.getNetworkId())
                .setHeader(HEADER_NETWORK_UUID, networkInfos.getNetworkUuid().toString())
                .setHeader(HEADER_CASE_FORMAT, caseFormatStr)
                .setHeader(HEADER_CASE_NAME, caseNameStr)
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_IMPORT_PARAMETERS, importParameters)
                .build());
    }

    public void emitNetworkExportFinished(UUID exportUuid, String receiver, String exportInfos, String error, String s3Key, String exportContentType) {
        sendNetworkExportFinishedMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_EXPORT_INFOS, exportInfos)
                .setHeader(HEADER_S3_KEY, s3Key)
                .setHeader(HEADER_EXPORT_UUID, exportUuid != null ? exportUuid.toString() : null)
                .setHeader(HEADER_ERROR, error)
                .setHeader(HEADER_EXPORT_CONTENT_TYPE, exportContentType)
                .build());
    }

    public void emitCaseExportFinished(UUID exportUuid, String userId, String error) {
        sendCaseExportFinishedMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_EXPORT_UUID, exportUuid != null ? exportUuid.toString() : null)
                .setHeader(HEADER_ERROR, error)
                .build());
    }

    public void emitNetworkExportStart(UUID networkUuid, String variantId, ExportInfos exportInfos) {
        sendNetworkExportStartMessage(MessageBuilder.withPayload(networkUuid)
                .setHeader(HEADER_VARIANT_ID, variantId)
                .setHeader(HEADER_FILE_NAME, exportInfos.getFilename())
                .setHeader(HEADER_FORMAT, exportInfos.getFormat())
                .setHeader(HEADER_RECEIVER, exportInfos.getReceiver())
                .setHeader(HEADER_EXPORT_INFOS, exportInfos.getExtraData())
                .setHeader(HEADER_EXPORT_UUID, exportInfos.getExportUuid() != null ? exportInfos.getExportUuid().toString() : null)
                .setHeader(HEADER_EXPORT_PARAMETERS, exportInfos.getFormatParameters())
                .build());
    }

    public void emitCaseExportStart(UUID caseUuid, String fileName, String format, String userId, UUID exportUuid, Map<String, Object> formatParameters) {
        sendCaseExportStartMessage(MessageBuilder.withPayload(caseUuid)
                .setHeader(HEADER_FILE_NAME, fileName)
                .setHeader(HEADER_FORMAT, format)
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_EXPORT_UUID, exportUuid != null ? exportUuid.toString() : null)
                .setHeader(HEADER_EXPORT_PARAMETERS, formatParameters)
                .build());
    }
}
