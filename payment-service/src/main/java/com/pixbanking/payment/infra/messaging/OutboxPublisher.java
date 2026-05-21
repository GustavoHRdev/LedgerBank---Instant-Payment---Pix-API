package com.pixbanking.payment.infra.messaging;

import com.pixbanking.payment.domain.model.OutboxEvent;
import com.pixbanking.payment.domain.model.OutboxEventStatus;
import com.pixbanking.payment.domain.repository.OutboxEventRepository;
import com.pixbanking.payment.infra.config.MessagingProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            MessagingProperties properties
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "PT2S")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findTopByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                PageRequest.of(0, properties.outboxPublishBatchSize())
        );

        for (OutboxEvent event : events) {
            rabbitTemplate.convertAndSend(
                    properties.pixEventsExchange(),
                    routingKeyFor(event.getEventType()),
                    event.getPayload(),
                    message -> {
                        message.getMessageProperties().setMessageId(event.getId().toString());
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setHeader("eventType", event.getEventType());
                        return message;
                    }
            );
            event.markPublished();
        }
    }

    private String routingKeyFor(String eventType) {
        return switch (eventType) {
            case "PIX_TRANSFER_COMPLETED" -> properties.transferCompletedRoutingKey();
            case "PIX_TRANSFER_FAILED" -> properties.transferFailedRoutingKey();
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + eventType);
        };
    }
}
