package com.powsybl.network.conversion.server.config;

import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.config.ListenerContainerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        /*
         * Using AtomicInteger as in org/springframework/cloud/stream/binder/rabbit/RabbitMessageChannelBinder.java
         * We expect cloud stream to call our customizer exactly once in order for each container so it will produce a sequence of increasing priorities
         */
        List<String> consumersToBalance = List.of("importGroup", "exportNetworkGroup", "exportCaseGroup");
        Map<String, AtomicInteger> consumerCounters = consumersToBalance.stream().collect(Collectors.toMap(Function.identity(), c -> new AtomicInteger(0)));
        return (container, destination, group) -> {
            if (container instanceof SimpleMessageListenerContainer smlc && consumerCounters.containsKey(group)) {
                smlc.setConsumerArguments(Map.of("x-priority", consumerCounters.get(group).getAndIncrement()));
            }
        };
    }
}
