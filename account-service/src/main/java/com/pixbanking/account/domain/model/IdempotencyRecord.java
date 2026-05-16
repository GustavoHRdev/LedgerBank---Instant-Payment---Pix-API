package com.pixbanking.account.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "transfer_request_id", nullable = false)
    private UUID transferRequestId;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(
            UUID id,
            UUID accountId,
            String idempotencyKey,
            String requestHash,
            UUID transferRequestId,
            String responseBody,
            String status,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.accountId = accountId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.transferRequestId = transferRequestId;
        this.responseBody = responseBody;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
