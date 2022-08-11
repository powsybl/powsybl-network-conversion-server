package com.powsybl.network.conversion.server;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.powsybl.network.conversion.server.dto.NetworkInfos;

@Service
public class NotificationService {

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private static final String HEADER_VARIANT_ID = "variantId";
    private static final String HEADER_REPORT_UUID = "reportUuid";
    private static final String HEADER_NETWORK_ID = "networkId";
    private static final String HEADER_NETWORK_UUID = "networkUuid";
    private static final String HEADER_RECEIVER = "receiver";
    private static final String HEADER_ERROR_MESSAGE = "errorMessage";

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

    public void emitCaseImportStart(UUID caseUuid, String variantId, UUID reportUuid, String receiver) {
        sendCaseImportStartMessage(MessageBuilder.withPayload(caseUuid)
                .setHeader(HEADER_VARIANT_ID, variantId)
                .setHeader(HEADER_REPORT_UUID, reportUuid)
                .setHeader(HEADER_RECEIVER, receiver)
                .build());
    }

    public void emitCaseImportSucceeded(NetworkInfos networkInfos, String receiver) {
        sendCaseImportSucceededMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_NETWORK_ID, networkInfos.getNetworkId())
                .setHeader(HEADER_NETWORK_UUID, networkInfos.getNetworkUuid())
                .setHeader(HEADER_RECEIVER, receiver)
                .build());
    }

    public void emitCaseImportFailed(String receiver, String errorMessage) {
        sendCaseImportFailedMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_ERROR_MESSAGE, errorMessage)
                .build());
    }
}
