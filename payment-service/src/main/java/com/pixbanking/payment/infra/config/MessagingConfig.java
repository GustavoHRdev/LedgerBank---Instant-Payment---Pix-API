package com.pixbanking.payment.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
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
    DirectExchange pixRequestedDlx(MessagingProperties properties) {
        return new DirectExchange(properties.pixRequestedDlx(), true, false);
    }

    @Bean
    Queue pixRequestedQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.pixRequestedQueue())
                .withArgument("x-dead-letter-exchange", properties.pixRequestedDlx())
                .withArgument("x-dead-letter-routing-key", properties.pixRequestedDlqRoutingKey())
                .build();
    }

    @Bean
    Queue pixRequestedDlq(MessagingProperties properties) {
        return QueueBuilder.durable(properties.pixRequestedDlq()).build();
    }

    @Bean
    Binding pixRequestedBinding(
            @Qualifier("pixRequestedQueue") Queue pixRequestedQueue,
            @Qualifier("pixRequestedExchange") DirectExchange pixRequestedExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder.bind(pixRequestedQueue)
                .to(pixRequestedExchange)
                .with(properties.pixRequestedRoutingKey());
    }

    @Bean
    Binding pixRequestedDlqBinding(
            @Qualifier("pixRequestedDlq") Queue pixRequestedDlq,
            @Qualifier("pixRequestedDlx") DirectExchange pixRequestedDlx,
            MessagingProperties properties
    ) {
        return BindingBuilder.bind(pixRequestedDlq)
                .to(pixRequestedDlx)
                .with(properties.pixRequestedDlqRoutingKey());
    }
}
