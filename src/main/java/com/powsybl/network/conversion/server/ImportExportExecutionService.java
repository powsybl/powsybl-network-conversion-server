/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import lombok.NonNull;

/**
 * @author Sylvain Bouzols <sylvain.bouzols_externe at rte-france.com>
 */
@Service
public class ImportExportExecutionService {
    private ThreadPoolExecutor executorService;

    public ImportExportExecutionService(@Value("${max-concurrent-import-export}") int maxConcurrentImportExport,
                                                    @NonNull NetworkConversionObserver networkConversionObserver) {
        executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxConcurrentImportExport);
        networkConversionObserver.createThreadPoolMetric(executorService);
    }

    @PreDestroy
    private void preDestroy() {
        executorService.shutdown();
    }

    public <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return CompletableFuture.supplyAsync(supplier, executorService);
    }
}
