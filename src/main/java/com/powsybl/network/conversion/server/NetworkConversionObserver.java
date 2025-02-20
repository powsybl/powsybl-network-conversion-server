/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.stereotype.Service;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */

@Service
public class NetworkConversionObserver {

    private static final String OBSERVATION_PREFIX = "app.conversion.";
    private static final String FORMAT_TAG_NAME = "format";

    private static final String IMPORT_OBSERVATION_NAME = OBSERVATION_PREFIX + "import";
    private static final String NUMBER_BUSES_IMPORTED_METER_NAME = IMPORT_OBSERVATION_NAME + ".buses";

    private static final String EXPORT_OBSERVATION_NAME = OBSERVATION_PREFIX + "export";
    private static final String NUMBER_BUSES_EXPORTED_METER_NAME = EXPORT_OBSERVATION_NAME + ".buses";

    private static final String TASK_TYPE_TAG_NAME = "type";
    private static final String TASK_TYPE_TAG_VALUE_CURRENT = "current";
    private static final String TASK_TYPE_TAG_VALUE_PENDING = "pending";
    private static final String TASK_POOL_METER_NAME_PREFIX = OBSERVATION_PREFIX + "tasks.pool.";

    private final ObservationRegistry observationRegistry;

    private final MeterRegistry meterRegistry;

    public NetworkConversionObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    public <E extends Throwable> ExportNetworkInfos observeExport(String format, Observation.CheckedCallable<ExportNetworkInfos, E> callable) throws E {
        Observation observation = createObservation(EXPORT_OBSERVATION_NAME, format);
        ExportNetworkInfos exportInfos = observation.observeChecked(callable);
        if (exportInfos != null) {
            recordNumberBuses(NUMBER_BUSES_EXPORTED_METER_NAME, format, exportInfos.getNumberBuses());
        }
        return exportInfos;
    }

    public <E extends Throwable> Network observeImport(String format, Observation.CheckedCallable<Network, E> callable) throws E {
        Network network = createObservation(IMPORT_OBSERVATION_NAME, format).observeChecked(callable);
        if (network != null) {
            recordNumberBuses(NUMBER_BUSES_IMPORTED_METER_NAME, format, network.getBusView().getBusStream().count());
        }
        return network;
    }

    private Observation createObservation(String name, String format) {
        return Observation.createNotStarted(name, observationRegistry)
                .lowCardinalityKeyValue(FORMAT_TAG_NAME, format);
    }

    private void recordNumberBuses(String meterName, String format, long numberBuses) {
        DistributionSummary.builder(meterName)
                .tags(FORMAT_TAG_NAME, format)
                .register(meterRegistry)
                .record(numberBuses);
    }

    public void createThreadPoolMetric(ThreadPoolExecutor threadPoolExecutor) {
        Gauge.builder(TASK_POOL_METER_NAME_PREFIX + TASK_TYPE_TAG_VALUE_CURRENT, threadPoolExecutor, ThreadPoolExecutor::getActiveCount)
            .description("The number of active import/export tasks in the thread pool")
            .tag(TASK_TYPE_TAG_NAME, TASK_TYPE_TAG_VALUE_CURRENT)
            .register(meterRegistry);
        Gauge.builder(TASK_POOL_METER_NAME_PREFIX + TASK_TYPE_TAG_VALUE_PENDING, threadPoolExecutor, executor -> executor.getQueue().size())
            .description("The number of pending import/export tasks in the thread pool")
            .tag(TASK_TYPE_TAG_NAME, TASK_TYPE_TAG_VALUE_PENDING)
            .register(meterRegistry);
    }
}
