package com.pixbanking.payment.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        String pixRequestedExchange,
        String pixRequestedQueue,
        String pixRequestedRoutingKey,
        String pixRequestedDlx,
        String pixRequestedDlq,
        String pixRequestedDlqRoutingKey,
        String consumerName
) {
}
