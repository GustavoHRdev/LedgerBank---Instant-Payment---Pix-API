package com.pixbanking.account.infra.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixbanking.account.domain.model.PixTransferFailedEvent;
import com.pixbanking.account.service.TransferNotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PixTransferFailedConsumer {

    private final ObjectMapper objectMapper;
    private final TransferNotificationService transferNotificationService;

    public PixTransferFailedConsumer(
            ObjectMapper objectMapper,
            TransferNotificationService transferNotificationService
    ) {
        this.objectMapper = objectMapper;
        this.transferNotificationService = transferNotificationService;
    }

    @RabbitListener(queues = "${app.messaging.failed-notification-queue}")
    public void onFailed(String payload, org.springframework.amqp.core.Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ message_id is required");
        }

        transferNotificationService.handleFailed(messageId, deserialize(payload));
    }

    private PixTransferFailedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, PixTransferFailedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not deserialize PIX transfer failed event", e);
        }
    }
}
