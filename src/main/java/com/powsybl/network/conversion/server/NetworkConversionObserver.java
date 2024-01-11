/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.springframework.stereotype.Service;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */

@Service
public class NetworkConversionObserver {

    private static final String OBSERVATION_PREFIX = "app.conversion.";
    private static final String FAILED_EXPORT_COUNTER_NAME = OBSERVATION_PREFIX + "export.failed";
    private static final String FAILED_IMPORT_COUNTER_NAME = OBSERVATION_PREFIX + "import.failed";

    private static final String FORMAT_NAME = "format";

    private static final String NUMBER_BUSES = "number of buses";

    private final ObservationRegistry observationRegistry;

    private final MeterRegistry meterRegistry;

    public NetworkConversionObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    public <E extends Throwable> ExportNetworkInfos observeExport(String name, String format, Observation.CheckedCallable<ExportNetworkInfos, E> callable) throws E {
        // Create an Observation object with the given name and format.
        Observation observation = createObservation(name, format);
        try {
            // Observe the execution of the callable within the context of the observation.
            // The observeChecked method captures the callable's execution details.
            return observation.observeChecked(() -> {
                // Call the callable and get the result.
                ExportNetworkInfos processResult = callable.call();
                // Add the number of buses as a key-value pair to the observation.
                observation.lowCardinalityKeyValue(NUMBER_BUSES, String.valueOf(processResult.getNumberBuses()));
                return processResult;
            });
        } catch (RuntimeException re) {
            incrementCount(FAILED_EXPORT_COUNTER_NAME, format);
            throw re;
        } catch (Throwable e) {
            incrementCount(FAILED_EXPORT_COUNTER_NAME, format);
            throw (E) e;
        }
    }

    public <E extends Throwable> Network observeImport(String name, String format, Observation.CheckedCallable<Network, E> callable) throws E {
        // Create an Observation object with the given name and format.
        Observation observation = createObservation(name, format);
        try {
            // Observe the execution of the callable within the context of the observation.
            // The observeChecked method captures the callable's execution details.
            return observation.observeChecked(() -> {
                // Call the callable and get the result.
                Network processResult = callable.call();
                // calculate the number of buses.
                long numberBuses = processResult.getBusView().getBusStream().count();
                // Add the number of buses as a key-value pair to the observation.
                observation.lowCardinalityKeyValue(NUMBER_BUSES, String.valueOf(numberBuses));
                return processResult;
            });
        } catch (RuntimeException re) {
            incrementCount(FAILED_IMPORT_COUNTER_NAME, format);
            throw re;
        } catch (Throwable e) {
            incrementCount(FAILED_IMPORT_COUNTER_NAME, format);
            throw (E) e;
        }
    }

    private Observation createObservation(String name, String format) {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(FORMAT_NAME, format);
    }

    private void incrementCount(String name, String format) {
        Counter.builder(name)
                .tag(FORMAT_NAME, format)
                .register(meterRegistry)
                .increment();
    }
}
