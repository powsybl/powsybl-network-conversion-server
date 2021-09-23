/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Service
public class NetworkConversionExecutionService {

    private ExecutorService executorService;

    @PostConstruct
    private void postConstruct() {
        executorService = Executors.newCachedThreadPool();
    }

    @PreDestroy
    private void preDestroy() {
        executorService.shutdown();
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executorService);
    }
}
