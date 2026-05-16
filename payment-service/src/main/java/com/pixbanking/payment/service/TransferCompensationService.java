package com.pixbanking.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixbanking.payment.domain.model.LedgerEntry;
import com.pixbanking.payment.domain.model.OutboxEvent;
import com.pixbanking.payment.domain.model.TransferRequest;
import com.pixbanking.payment.domain.repository.LedgerRepository;
import com.pixbanking.payment.domain.repository.OutboxEventRepository;
import com.pixbanking.payment.domain.repository.TransferRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferCompensationService {

    private final LedgerRepository ledgerRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TransferCompensationService(
            LedgerRepository ledgerRepository,
            TransferRequestRepository transferRequestRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.ledgerRepository = ledgerRepository;
        this.transferRequestRepository = transferRequestRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void reverse(UUID transferId, UUID payerAccountId, BigDecimal amount) {
        TransferRequest transfer = transferRequestRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        BigDecimal currentBalance = ledgerRepository.findLastRunningBalance(payerAccountId)
                .orElseGet(() -> ledgerRepository.calculateBalance(payerAccountId).setScale(4, RoundingMode.UNNECESSARY));
        BigDecimal restoredBalance = currentBalance.add(amount);

        LedgerEntry reversalEntry = LedgerEntry.of(
                payerAccountId,
                transferId,
                LedgerEntry.EntryType.REVERSAL_CREDIT,
                amount,
                restoredBalance
        );
        ledgerRepository.save(reversalEntry);

        transfer.markReversed("CREDIT_STEP_FAILED", Instant.now());
        outboxEventRepository.save(OutboxEvent.of(
                "TRANSFER",
                transferId,
                "PIX_TRANSFER_REVERSED",
                Map.of(
                        "transferId", transferId,
                        "payerAccountId", payerAccountId,
                        "amount", amount.toPlainString(),
                        "reversalLedgerId", reversalEntry.getId(),
                        "reason", "CREDIT_STEP_FAILED"
                ),
                objectMapper
        ));
    }
}
