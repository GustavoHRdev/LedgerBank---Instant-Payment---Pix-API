package com.pixbanking.account.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PixTransferFailedEvent(
        UUID transferId,
        String reason,
        OffsetDateTime failedAt
) {
}
