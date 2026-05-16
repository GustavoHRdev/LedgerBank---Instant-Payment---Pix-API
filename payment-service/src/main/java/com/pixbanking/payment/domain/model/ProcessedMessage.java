package com.pixbanking.payment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @Id
    private UUID id;

    @Column(name = "message_id", nullable = false, unique = true, length = 100)
    private String messageId;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedMessage() {
    }

    public static ProcessedMessage of(String messageId, String consumerName) {
        return new ProcessedMessage(UUID.randomUUID(), messageId, consumerName, Instant.now());
    }

    public ProcessedMessage(UUID id, String messageId, String consumerName, Instant processedAt) {
        this.id = id;
        this.messageId = messageId;
        this.consumerName = consumerName;
        this.processedAt = processedAt;
    }
}
