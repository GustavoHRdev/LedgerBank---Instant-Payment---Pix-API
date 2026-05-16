package com.pixbanking.account.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PixTransferRequestedEvent(
        UUID transferRequestId,
        UUID sourceAccountId,
        String destinationPixKey,
        String amount,
        String currency,
        String idempotencyKey,
        OffsetDateTime createdAt
) {
}
