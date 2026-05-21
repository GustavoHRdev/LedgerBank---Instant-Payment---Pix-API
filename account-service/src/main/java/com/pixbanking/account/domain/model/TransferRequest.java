package com.pixbanking.account.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    private TransferRequestStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected TransferRequest() {
    }

    public TransferRequest(
            UUID id,
            UUID sourceAccountId,
            String destinationPixKey,
            BigDecimal amount,
            String currency,
            TransferRequestStatus status,
            String idempotencyKey,
            String requestHash,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime completedAt,
            String failureReason
    ) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.destinationPixKey = destinationPixKey;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public String getDestinationPixKey() {
        return destinationPixKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferRequestStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void markCompleted(OffsetDateTime completedAt) {
        this.status = TransferRequestStatus.COMPLETED;
        this.updatedAt = completedAt;
        this.completedAt = completedAt;
        this.failureReason = null;
    }

    public void markFailed(String failureReason, OffsetDateTime failedAt) {
        this.status = TransferRequestStatus.FAILED;
        this.updatedAt = failedAt;
        this.failureReason = failureReason;
    }
}
