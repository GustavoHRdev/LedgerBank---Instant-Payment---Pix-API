package com.pixbanking.account.dto;

import java.util.UUID;

public record TransferCreatedResponse(
        UUID transferRequestId,
        String status
) {
}
