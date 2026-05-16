package com.pixbanking.payment.service;

import java.util.UUID;

public record LiquidationResult(
        boolean success,
        UUID debitEntryId,
        UUID creditEntryId,
        String reason
) {
    public static LiquidationResult success(UUID debitEntryId, UUID creditEntryId) {
        return new LiquidationResult(true, debitEntryId, creditEntryId, null);
    }

    public static LiquidationResult failure(String reason) {
        return new LiquidationResult(false, null, null, reason);
    }
}
