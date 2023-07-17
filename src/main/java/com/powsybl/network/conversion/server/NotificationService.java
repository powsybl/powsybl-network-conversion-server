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

import com.powsybl.network.conversion.server.dto.CaseInfos;
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
    public static final String HEADER_ERROR_MESSAGE = "errorMessage";
    public static final String HEADER_IMPORT_PARAMETERS = "importParameters";
    public static final String HEADER_CASE_FORMAT = "caseFormat";
    public static final String HEADER_CASE_NAME = "caseName";

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

    private void sendCaseImportFailedMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending import failed message : {}", message);
        networkConversionPublisher.send("publishCaseImportFailed-out-0", message);
    }

    public void emitCaseImportStart(UUID caseUuid, String variantId, UUID reportUuid, Map<String, Object> importParameters, String receiver) {
        sendCaseImportStartMessage(MessageBuilder.withPayload(caseUuid)
                .setHeader(HEADER_VARIANT_ID, variantId)
                .setHeader(HEADER_REPORT_UUID, reportUuid != null ? reportUuid.toString() : null)
                .setHeader(HEADER_IMPORT_PARAMETERS, importParameters)
                .setHeader(HEADER_RECEIVER, receiver)
                .build());
    }

    public void emitCaseImportSucceeded(NetworkInfos networkInfos, CaseInfos caseInfos, String receiver, Map<String, String> importParameters) {
        sendCaseImportSucceededMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_NETWORK_ID, networkInfos.getNetworkId())
                .setHeader(HEADER_NETWORK_UUID, networkInfos.getNetworkUuid().toString())
                .setHeader(HEADER_CASE_FORMAT, caseInfos.getFormat())
                .setHeader(HEADER_CASE_NAME, caseInfos.getName())
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_IMPORT_PARAMETERS, importParameters)
                .build());
    }

    public void emitCaseImportFailed(String receiver, String errorMessage) {
        sendCaseImportFailedMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_ERROR_MESSAGE, errorMessage)
                .build());
    }
}
