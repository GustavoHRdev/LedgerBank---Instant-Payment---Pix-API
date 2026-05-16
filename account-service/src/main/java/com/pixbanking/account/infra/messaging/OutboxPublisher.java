package com.pixbanking.account.infra.messaging;

import com.pixbanking.account.domain.model.OutboxEvent;
import com.pixbanking.account.domain.model.OutboxEventStatus;
import com.pixbanking.account.domain.model.OutboxEventType;
import com.pixbanking.account.domain.repository.OutboxEventRepository;
import com.pixbanking.account.infra.config.MessagingProperties;
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
            try {
                rabbitTemplate.convertAndSend(
                        properties.pixRequestedExchange(),
                        routingKeyFor(event),
                        event.getPayload(),
                        message -> {
                            message.getMessageProperties().setMessageId(event.getId().toString());
                            message.getMessageProperties().setContentType("application/json");
                            message.getMessageProperties().setHeader("eventType", event.getEventType());
                            return message;
                        }
                );
                event.markPublished();
            } catch (RuntimeException ex) {
                // Keep the event pending so the next scheduler cycle can retry publication.
            }
        }
    }

    private String routingKeyFor(OutboxEvent event) {
        if (OutboxEventType.PIX_TRANSFER_REQUESTED.equals(event.getEventType())) {
            return properties.pixRequestedRoutingKey();
        }
        throw new IllegalArgumentException("Unsupported outbox event type: " + event.getEventType());
    }
}
