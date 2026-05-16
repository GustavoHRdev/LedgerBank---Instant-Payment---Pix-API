package com.pixbanking.payment.service;

import java.math.BigDecimal;
import java.util.UUID;

public class PartialExecutionException extends RuntimeException {

    private final UUID transferId;
    private final UUID payerAccountId;
    private final BigDecimal amount;

    public PartialExecutionException(UUID transferId, UUID payerAccountId, BigDecimal amount, Throwable cause) {
        super("Transfer partially executed", cause);
        this.transferId = transferId;
        this.payerAccountId = payerAccountId;
        this.amount = amount;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getPayerAccountId() {
        return payerAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
