/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */

@Service
public class NetworkConversionObserver {

    private static final String OBSERVATION_PREFIX = "app.conversion.";
    private static final String FORMAT_NAME = "format";

    private static final String NUMBER_BUSES_EXPORTED_NAME = OBSERVATION_PREFIX + "buses.exported";
    private static final String NUMBER_BUSES_IMPORTED_NAME = OBSERVATION_PREFIX + "buses.imported";

    private final ObservationRegistry observationRegistry;

    private final MeterRegistry meterRegistry;

    private Map<String, Double> exportFormats = new HashMap<>();
    private Map<String, Double> importFormats = new HashMap<>();

    public NetworkConversionObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    public <E extends Throwable> ExportNetworkInfos observeExport(String name, String format, Observation.CheckedCallable<ExportNetworkInfos, E> callable) throws E {
        ExportNetworkInfos result = createObservation(name, format).observeChecked(callable);
        Optional.ofNullable(result)
                .ifPresent(r -> observeExportNetworkSize(NUMBER_BUSES_EXPORTED_NAME, format, result.getNumberBuses()));
        return result;
    }

    public <E extends Throwable> Network observeImport(String name, String format, Observation.CheckedCallable<Network, E> callable) throws E {
        Network result = createObservation(name, format).observeChecked(callable);
        Optional.ofNullable(result)
                .ifPresent(r -> observeImportNetworkSize(NUMBER_BUSES_IMPORTED_NAME, format, r.getBusView().getBusStream().count()));
        return result;
    }

    private Observation createObservation(String name, String format) {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(FORMAT_NAME, format);
    }

    private void observeExportNetworkSize(String name, String format, double networkSize) {
        exportFormats.put(format, networkSize);
        Gauge.builder(name, exportFormats, map -> map.get(format))
                .tags("format", format)
                .register(meterRegistry);
    }

    private void observeImportNetworkSize(String name, String format, double networkSize) {
        importFormats.put(format, networkSize);
        Gauge.builder(name, importFormats, map -> map.get(format))
                .tags("format", format)
                .register(meterRegistry);
    }
}
