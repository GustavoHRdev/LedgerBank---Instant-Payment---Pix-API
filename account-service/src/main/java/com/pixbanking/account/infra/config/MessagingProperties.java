package com.pixbanking.account.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        String pixRequestedExchange,
        String pixRequestedRoutingKey,
        int outboxPublishBatchSize
) {
}
