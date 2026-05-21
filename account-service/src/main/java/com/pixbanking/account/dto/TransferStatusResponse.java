package com.pixbanking.account.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferStatusResponse(
        UUID transferId,
        String status,
        String amount,
        String destinationPixKey,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        String failureReason
) {
}
