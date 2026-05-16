package com.pixbanking.payment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pix_keys")
public class PixKey {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, unique = true, length = 77)
    private String value;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PixKey() {
    }

    public PixKey(UUID id, UUID accountId, String value, boolean active, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.value = value;
        this.active = active;
        this.createdAt = createdAt;
    }

    public UUID getAccountId() {
        return accountId;
    }
}
