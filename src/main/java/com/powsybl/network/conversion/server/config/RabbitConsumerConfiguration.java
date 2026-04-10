/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server.config;

import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.config.ListenerContainerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Kevin Le Saulnier <kevin.le-saulnier at rte-france.com>
 */
@Configuration
public class RabbitConsumerConfiguration {
    /*
     * RabbitMQ consumer priority:
     * https://www.rabbitmq.com/docs/consumer-priority
     *
     * Each container creates exactly one AMQP consumer with prefetch=1 and its own priority.
     * When dispatching messages, RabbitMQ always selects the highest-priority consumer
     * that is available.
     */
    @Bean
    public ListenerContainerCustomizer<MessageListenerContainer> customizer(BindingServiceProperties bindingServiceProperties) {
        List<String> loadBalancedGroups = List.of("importGroup", "exportNetworkGroup", "exportCaseGroup");
        Map<String, AtomicInteger> groupIndexes = new ConcurrentHashMap<>();
        /*
         * Using AtomicInteger as in org/springframework/cloud/stream/binder/rabbit/RabbitMessageChannelBinder.java
         * We expect cloud stream to call our customizer exactly once in order for each container so it will produce a sequence of increasing priorities
         */
        return (container, destination, group) -> {
            if (!loadBalancedGroups.contains(group) || !(container instanceof SimpleMessageListenerContainer smlc)) {
                return;
            }

            AtomicInteger index = groupIndexes.computeIfAbsent(group, g -> new AtomicInteger());

            smlc.setConsumerArguments(Map.of(
                "x-priority", index.getAndIncrement()
            ));
        };
    }
}
