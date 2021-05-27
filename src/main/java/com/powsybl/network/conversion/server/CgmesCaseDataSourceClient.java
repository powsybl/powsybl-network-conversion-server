/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.cases.datasource.CaseDataSourceClient;
import com.powsybl.commons.PowsyblException;
import com.powsybl.network.conversion.server.dto.BoundaryInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class CgmesCaseDataSourceClient extends CaseDataSourceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CgmesCaseDataSourceClient.class);

    public static final String TPBD_FILE_REGEX = "^(([^_][^_])*?(__ENTSOE_TPBD_).*(\\.xml))$";
    public static final String EQBD_FILE_REGEX = "^(([^_][^_])*?(__ENTSOE_EQBD_).*(\\.xml))$";

    private List<BoundaryInfos> boundaries;

    public CgmesCaseDataSourceClient(RestTemplate restTemplate, UUID caseUuid, List<BoundaryInfos> boundaries) {
        super(restTemplate, caseUuid);
        this.boundaries = boundaries;
    }

    @Override
    public InputStream newInputStream(String fileName) {
        if (!fileName.matches(EQBD_FILE_REGEX) && !fileName.matches(TPBD_FILE_REGEX)) {
            return super.newInputStream(fileName);
        } else {
            Optional<BoundaryInfos> replacingBoundary;
            if (fileName.matches(EQBD_FILE_REGEX)) {
                replacingBoundary = boundaries.stream().filter(b -> b.getFilename().matches(EQBD_FILE_REGEX)).findFirst();
            } else {
                replacingBoundary = boundaries.stream().filter(b -> b.getFilename().matches(TPBD_FILE_REGEX)).findFirst();
            }
            if (replacingBoundary.isPresent()) {
                LOGGER.info("Using boundary file {} with id {} to replace original boundary file {}",
                    replacingBoundary.get().getFilename(),
                    replacingBoundary.get().getId(),
                    fileName);
                return new ByteArrayInputStream(replacingBoundary.get().getBoundary().getBytes(StandardCharsets.UTF_8));
            } else {
                throw new PowsyblException("No replacing boundary available for replacement of boundary " + fileName + " !!");
            }
        }
    }
}
