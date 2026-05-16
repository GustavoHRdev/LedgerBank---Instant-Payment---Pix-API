package com.pixbanking.account.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.pixbanking.account.infra.json.StringBackedBigDecimalDeserializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID sourceAccountId,
        @NotBlank String destinationPixKey,
        @NotNull
        @DecimalMin(value = "0.0001", inclusive = true)
        @JsonDeserialize(using = StringBackedBigDecimalDeserializer.class)
        BigDecimal amount,
        @NotBlank String currency
) {
}
