package com.powsybl.network.conversion.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.config.ListenerContainerCustomizer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitConsumerConfigurationTest {
    private RabbitConsumerConfiguration configuration;
    private ListenerContainerCustomizer<MessageListenerContainer> customizer;

    @BeforeEach
    void setUp() {
        configuration = new RabbitConsumerConfiguration();
        BindingServiceProperties bindingServiceProperties = Mockito.mock(BindingServiceProperties.class);
        customizer = configuration.customizer(bindingServiceProperties);
    }

    @Test
    void shouldSetPriorityAndIncrementPerGroup() {
        SimpleMessageListenerContainer container1 = new SimpleMessageListenerContainer();
        SimpleMessageListenerContainer container2 = new SimpleMessageListenerContainer();

        customizer.configure(container1, "destination", "importGroup");
        customizer.configure(container2, "destination", "importGroup");

        Map<String, Object> args1 = container1.getConsumerArguments();
        Map<String, Object> args2 = container2.getConsumerArguments();

        assertThat(args1).containsEntry("x-priority", 0);
        assertThat(args2).containsEntry("x-priority", 1);
    }

    @Test
    void shouldUseDifferentCountersPerGroupAndIncrementIndependently() {
        SimpleMessageListenerContainer importContainer1 = new SimpleMessageListenerContainer();
        SimpleMessageListenerContainer importContainer2 = new SimpleMessageListenerContainer();
        SimpleMessageListenerContainer exportContainer1 = new SimpleMessageListenerContainer();

        customizer.configure(importContainer1, "destination", "importGroup");
        customizer.configure(importContainer2, "destination", "importGroup");

        customizer.configure(exportContainer1, "destination", "exportNetworkGroup");

        assertThat(importContainer1.getConsumerArguments()).containsEntry("x-priority", 0);
        assertThat(importContainer2.getConsumerArguments()).containsEntry("x-priority", 1);

        assertThat(exportContainer1.getConsumerArguments()).containsEntry("x-priority", 0);
    }

    @Test
    void shouldIgnoreUnknownGroup() {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        customizer.configure(container, "destination", "unknownGroup");

        assertThat(container.getConsumerArguments()).isEmpty();
    }
}