package com.pixbanking.account.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        String pixRequestedExchange,
        String pixRequestedRoutingKey,
        String pixEventsExchange,
        String transferCompletedRoutingKey,
        String transferFailedRoutingKey,
        String completedNotificationQueue,
        String failedNotificationQueue,
        String notificationConsumerName,
        int outboxPublishBatchSize
) {
}
