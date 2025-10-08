/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */

@Service
public class NetworkConversionObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionObserver.class);

    private static final String OBSERVATION_PREFIX = "app.conversion.";
    private static final String FORMAT_TAG_NAME = "format";

    private static final String IMPORT_OBSERVATION_NAME = OBSERVATION_PREFIX + "import";
    private static final String IMPORT_TOTAL_OBSERVATION_NAME = OBSERVATION_PREFIX + "import.total";
    private static final String IMPORT_PROCESSING_OBSERVATION_NAME = OBSERVATION_PREFIX + "import.processing";
    private static final String NUMBER_BUSES_IMPORTED_METER_NAME = IMPORT_OBSERVATION_NAME + ".buses";

    private static final String EXPORT_OBSERVATION_NAME = OBSERVATION_PREFIX + "export";
    private static final String EXPORT_TOTAL_OBSERVATION_NAME = OBSERVATION_PREFIX + "export.total";
    private static final String EXPORT_PROCESSING_OBSERVATION_NAME = OBSERVATION_PREFIX + "export.processing";
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

    public <T, E extends Throwable> T observeExportTotal(String format, Observation.CheckedCallable<T, E> callable) throws E {
        return createObservation(EXPORT_TOTAL_OBSERVATION_NAME, format).observeChecked(callable);
    }

    public <T, E extends Throwable> CompletableFuture<T> observeExportTotalAsync(String format, Observation.CheckedCallable<CompletableFuture<T>, E> callable) throws E {
        var obs = createObservation(EXPORT_TOTAL_OBSERVATION_NAME, format);
        var cf = observeCheckedAsync(obs, callable);
        return cf;
    }

    public <E extends Throwable> ExportNetworkInfos observeExportProcessing(String format, Observation.CheckedCallable<ExportNetworkInfos, E> callable) throws E {
        ExportNetworkInfos exportInfos = createObservation(EXPORT_PROCESSING_OBSERVATION_NAME, format).observeChecked(callable);
        if (exportInfos != null) {
            recordNumberBuses(NUMBER_BUSES_EXPORTED_METER_NAME, format, exportInfos.getNumberBuses());
        }
        return exportInfos;
    }

    public <T, E extends Throwable> T observeImportTotal(String format, Observation.CheckedCallable<T, E> callable) throws E {
        return createObservation(IMPORT_TOTAL_OBSERVATION_NAME, format).observeChecked(callable);
    }

    public <E extends Throwable> Network observeImportProcessing(String format, Observation.CheckedCallable<Network, E> callable) throws E {
        Network network = createObservation(IMPORT_PROCESSING_OBSERVATION_NAME, format).observeChecked(callable);
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

    // Written by copying the source code of micrometer in Observation.observeChecked
    // Does this code already exist somewhere in a lib that we can just use instead ?
    // If so, we should use it instead of writing/maintaining it ourselves
    public static <T, E extends Throwable> CompletableFuture<T> observeCheckedAsync(Observation obs, Observation.CheckedCallable<CompletableFuture<T>, E> callable) throws E {
        obs.start();
        CompletableFuture<T> cf = null;
        try (Scope scope = obs.openScope()) {
            cf = callable.call().whenComplete((res, ex) -> {
                if (ex != null) {
                    obs.error(ex);
                }
                // Is there an openScope in this thread from ContextExecutorService ? if so who closes it and when ?
                //can we call observation.stop() before this ?
                obs.stop();
            });
            return cf;
        } catch (Throwable t) {
            // Here there could have been an exception before or after constructing the completablefuture.
            // We need to cleanup only when we will not reach the whenComplete above, that is when cf is null
            if (cf != null) {
                try {
                    // because we can't use finally to close, we must defensively suppress any exceptions
                    // in obs.error(), otherwise obs.stop() won't be called just after.
                    obs.error(t);
                } catch (Throwable t2) {
                    LOGGER.error("Exception when trying to signal error", t2);
                    t.addSuppressed(t2);
                }
                // Can't put stop in the finally, must put it here because if the completable future
                // is constructed the cleanup happens after the completablefuture was completed in whenComplete.
                obs.stop();
            }
            throw t;
        }
    }
}
