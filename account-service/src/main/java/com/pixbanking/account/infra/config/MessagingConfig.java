package com.pixbanking.account.infra.config;

import org.springframework.amqp.core.DirectExchange;
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
}
