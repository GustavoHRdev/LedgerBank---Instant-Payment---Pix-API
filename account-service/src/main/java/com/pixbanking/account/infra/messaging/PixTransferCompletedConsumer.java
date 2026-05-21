package com.pixbanking.account.infra.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixbanking.account.domain.model.PixTransferCompletedEvent;
import com.pixbanking.account.service.TransferNotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PixTransferCompletedConsumer {

    private final ObjectMapper objectMapper;
    private final TransferNotificationService transferNotificationService;

    public PixTransferCompletedConsumer(
            ObjectMapper objectMapper,
            TransferNotificationService transferNotificationService
    ) {
        this.objectMapper = objectMapper;
        this.transferNotificationService = transferNotificationService;
    }

    @RabbitListener(queues = "${app.messaging.completed-notification-queue}")
    public void onCompleted(String payload, org.springframework.amqp.core.Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ message_id is required");
        }

        transferNotificationService.handleCompleted(messageId, deserialize(payload));
    }

    private PixTransferCompletedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, PixTransferCompletedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not deserialize PIX transfer completed event", e);
        }
    }
}
