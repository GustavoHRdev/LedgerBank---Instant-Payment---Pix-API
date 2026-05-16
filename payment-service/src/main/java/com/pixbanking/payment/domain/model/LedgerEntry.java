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
@Table(name = "ledger_entries")
public class LedgerEntry {

    public enum EntryType {
        CREDIT,
        DEBIT,
        REVERSAL_CREDIT,
        REVERSAL_DEBIT
    }

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "running_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal runningBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public static LedgerEntry of(
            UUID accountId,
            UUID transferId,
            EntryType entryType,
            BigDecimal amount,
            BigDecimal runningBalance
    ) {
        LedgerEntry entry = new LedgerEntry();
        entry.id = UUID.randomUUID();
        entry.accountId = accountId;
        entry.transferId = transferId;
        entry.entryType = entryType;
        entry.amount = amount;
        entry.runningBalance = runningBalance;
        entry.createdAt = Instant.now();
        return entry;
    }

    public UUID getId() {
        return id;
    }
}
