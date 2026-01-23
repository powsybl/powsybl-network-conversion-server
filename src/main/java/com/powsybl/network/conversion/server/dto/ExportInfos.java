package com.powsybl.network.conversion.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@Getter
public class ExportInfos {
    private String filename;

    private UUID exportUuid;

    private String format;

    private String receiver;

    private Map<String, Object> formatParameters;

    private String extraData;
}
