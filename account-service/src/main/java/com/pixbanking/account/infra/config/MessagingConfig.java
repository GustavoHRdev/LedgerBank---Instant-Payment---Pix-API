package com.pixbanking.account.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfig {

    @Bean
    DirectExchange pixRequestedExchange(MessagingProperties properties) {
        return new DirectExchange(properties.pixRequestedExchange(), true, false);
    }

    @Bean
    DirectExchange pixEventsExchange(MessagingProperties properties) {
        return new DirectExchange(properties.pixEventsExchange(), true, false);
    }

    @Bean
    Queue completedNotificationQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.completedNotificationQueue()).build();
    }

    @Bean
    Queue failedNotificationQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.failedNotificationQueue()).build();
    }

    @Bean
    Binding transferCompletedBinding(
            @Qualifier("completedNotificationQueue") Queue completedNotificationQueue,
            @Qualifier("pixEventsExchange") DirectExchange pixEventsExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder.bind(completedNotificationQueue)
                .to(pixEventsExchange)
                .with(properties.transferCompletedRoutingKey());
    }

    @Bean
    Binding transferFailedBinding(
            @Qualifier("failedNotificationQueue") Queue failedNotificationQueue,
            @Qualifier("pixEventsExchange") DirectExchange pixEventsExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder.bind(failedNotificationQueue)
                .to(pixEventsExchange)
                .with(properties.transferFailedRoutingKey());
    }
}
