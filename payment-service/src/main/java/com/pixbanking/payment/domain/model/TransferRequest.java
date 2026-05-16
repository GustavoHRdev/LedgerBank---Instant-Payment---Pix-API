package com.pixbanking.payment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_requests")
public class TransferRequest {

    @Id
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_pix_key", nullable = false, length = 77)
    private String destinationPixKey;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransferRequest() {
    }

    public TransferRequest(
            UUID id,
            UUID sourceAccountId,
            String destinationPixKey,
            BigDecimal amount,
            String currency,
            TransferStatus status,
            String failureReason,
            String idempotencyKey,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.destinationPixKey = destinationPixKey;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.failureReason = failureReason;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDestinationPixKey() {
        return destinationPixKey;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void markProcessing(Instant now) {
        this.status = TransferStatus.PROCESSING;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void markCompleted(Instant now) {
        this.status = TransferStatus.COMPLETED;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void markFailed(String failureReason, Instant now) {
        this.status = TransferStatus.FAILED;
        this.failureReason = failureReason;
        this.updatedAt = now;
    }

    public void markReversed(String failureReason, Instant now) {
        this.status = TransferStatus.REVERSED;
        this.failureReason = failureReason;
        this.updatedAt = now;
    }
}
