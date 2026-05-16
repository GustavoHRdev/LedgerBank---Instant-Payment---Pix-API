package com.pixbanking.payment.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixbanking.payment.domain.model.PixTransferRequestedEvent;
import com.pixbanking.payment.service.TransferProcessingOrchestrator;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PixTransferRequestedConsumer {

    private final ObjectMapper objectMapper;
    private final TransferProcessingOrchestrator transferProcessingOrchestrator;

    public PixTransferRequestedConsumer(
            ObjectMapper objectMapper,
            TransferProcessingOrchestrator transferProcessingOrchestrator
    ) {
        this.objectMapper = objectMapper;
        this.transferProcessingOrchestrator = transferProcessingOrchestrator;
    }

    @RabbitListener(queues = "${app.messaging.pix-requested-queue}")
    public void consume(String payload, org.springframework.amqp.core.Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ message_id is required");
        }

        transferProcessingOrchestrator.process(messageId, deserialize(payload));
    }

    private PixTransferRequestedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, PixTransferRequestedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not deserialize PIX transfer event", e);
        }
    }
}
