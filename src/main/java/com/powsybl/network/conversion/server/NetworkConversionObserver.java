/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

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
    private static final String CONVERSION_FAILED_NAME = OBSERVATION_PREFIX + "failed";

    private static final String FORMAT_NAME = "format";

    private static final String NETWORK_DATA_SIZE = "NetworkDataSize";

    private final ObservationRegistry observationRegistry;

    private final MeterRegistry meterRegistry;

    public NetworkConversionObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    public <T, E extends Throwable> T observeExport(String name, String format, Observation.CheckedCallable<T, E> callable) throws E {
        // Create an Observation object with the given name and format.
        Observation observation = createObservation(name, format);

        try {
            // Observe the execution of the callable within the context of the observation.
            // The observeChecked method captures the callable's execution details.
            T result = observation.observeChecked(() -> {
                // Call the callable and get the result.
                T processResult = callable.call();

                // Check if the result is an instance of ExportNetworkInfos.
                // If it is, calculate the size of the network data in kilobytes.
                if (processResult instanceof ExportNetworkInfos) {
                    double sizeInKo = ((ExportNetworkInfos) processResult).getNetworkData().length / 1024.0;
                    // Add the calculated size as a key-value pair to the observation.
                    observation.lowCardinalityKeyValue(NETWORK_DATA_SIZE, String.format("%.2f", sizeInKo) + " Ko");
                }
                if (processResult != null) {
                    incrementCount(CONVERSION_FAILED_NAME, format);
                }
                return processResult;
            });

            return result;
        } catch (RuntimeException re) {
            throw re;
        } catch (Throwable e) {
            throw (E) e;
        }
    }

    public <T, E extends Throwable> T observeImport(String name, String format, Long size, Observation.CheckedCallable<T, E> callable) throws E {
        T result = createObservationImport(name, format, size).observeChecked(callable);
        if (result != null) {
            incrementCount(CONVERSION_FAILED_NAME, format);
        }
        return result;
    }

    private Observation createObservationImport(String name, String format, Long size) {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(FORMAT_NAME, format)
                .lowCardinalityKeyValue(NETWORK_DATA_SIZE, size.toString() + " Ko");
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
