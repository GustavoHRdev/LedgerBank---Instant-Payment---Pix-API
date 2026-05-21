package com.pixbanking.account.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PixTransferCompletedEvent(
        UUID transferId,
        UUID debitEntryId,
        UUID creditEntryId,
        String amount,
        String currency,
        OffsetDateTime completedAt
) {
}
