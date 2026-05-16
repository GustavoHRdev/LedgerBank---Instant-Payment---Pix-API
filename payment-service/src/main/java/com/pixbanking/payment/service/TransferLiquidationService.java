package com.pixbanking.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixbanking.payment.domain.model.Account;
import com.pixbanking.payment.domain.model.AccountStatus;
import com.pixbanking.payment.domain.model.LedgerEntry;
import com.pixbanking.payment.domain.model.OutboxEvent;
import com.pixbanking.payment.domain.model.PixKey;
import com.pixbanking.payment.domain.model.PixTransferRequestedEvent;
import com.pixbanking.payment.domain.model.TransferRequest;
import com.pixbanking.payment.domain.model.TransferStatus;
import com.pixbanking.payment.domain.repository.AccountRepository;
import com.pixbanking.payment.domain.repository.LedgerRepository;
import com.pixbanking.payment.domain.repository.OutboxEventRepository;
import com.pixbanking.payment.domain.repository.PixKeyRepository;
import com.pixbanking.payment.domain.repository.TransferRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

@Service
public class TransferLiquidationService {

    private final AccountRepository accountRepository;
    private final PixKeyRepository pixKeyRepository;
    private final LedgerRepository ledgerRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TransferLiquidationService(
            AccountRepository accountRepository,
            PixKeyRepository pixKeyRepository,
            LedgerRepository ledgerRepository,
            TransferRequestRepository transferRequestRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.pixKeyRepository = pixKeyRepository;
        this.ledgerRepository = ledgerRepository;
        this.transferRequestRepository = transferRequestRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LiquidationResult liquidate(PixTransferRequestedEvent event) {
        TransferRequest transfer = loadOrCreateTransfer(event);
        transfer.markProcessing(Instant.now());

        Account payer = accountRepository.findByIdWithLock(event.sourceAccountId())
                .orElseThrow(() -> new BusinessRuleException("PAYER_ACCOUNT_NOT_FOUND", "Payer account not found"));

        if (payer.getStatus() != AccountStatus.ACTIVE) {
            return failTransfer(transfer, "PAYER_ACCOUNT_INACTIVE");
        }

        if (!payer.getCurrency().equals(event.currency())) {
            return failTransfer(transfer, "PAYER_CURRENCY_MISMATCH");
        }

        PixKey payeeKey = pixKeyRepository.findByValueAndActiveTrue(event.destinationPixKey())
                .orElseThrow(() -> new BusinessRuleException("PIX_KEY_NOT_FOUND", "Destination PIX key not found"));

        Account payee = accountRepository.findById(payeeKey.getAccountId())
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessRuleException("PAYEE_ACCOUNT_INACTIVE", "Payee account inactive"));

        BigDecimal amount = parseAmount(event.amount());
        BigDecimal payerBalance = currentBalance(payer.getId());
        if (payerBalance.compareTo(amount) < 0) {
            return failTransfer(transfer, "INSUFFICIENT_BALANCE");
        }

        BigDecimal payerNewBalance = payerBalance.subtract(amount);
        LedgerEntry debitEntry = LedgerEntry.of(
                payer.getId(),
                event.transferRequestId(),
                LedgerEntry.EntryType.DEBIT,
                amount,
                payerNewBalance
        );
        ledgerRepository.save(debitEntry);

        BigDecimal payeeBalance = currentBalance(payee.getId());
        BigDecimal payeeNewBalance = payeeBalance.add(amount);
        LedgerEntry creditEntry = LedgerEntry.of(
                payee.getId(),
                event.transferRequestId(),
                LedgerEntry.EntryType.CREDIT,
                amount,
                payeeNewBalance
        );
        ledgerRepository.save(creditEntry);

        transfer.markCompleted(Instant.now());
        outboxEventRepository.save(OutboxEvent.of(
                "TRANSFER",
                event.transferRequestId(),
                "PIX_TRANSFER_COMPLETED",
                Map.of(
                        "transferId", event.transferRequestId(),
                        "debitEntryId", debitEntry.getId(),
                        "creditEntryId", creditEntry.getId(),
                        "amount", amount.toPlainString(),
                        "currency", event.currency()
                ),
                objectMapper
        ));

        return LiquidationResult.success(debitEntry.getId(), creditEntry.getId());
    }

    @Transactional
    public LiquidationResult failTransfer(PixTransferRequestedEvent event, String reason) {
        TransferRequest transfer = loadOrCreateTransfer(event);
        return failTransfer(transfer, reason);
    }

    @Transactional
    public LiquidationResult failTransfer(TransferRequest transfer, String reason) {
        transfer.markFailed(reason, Instant.now());
        outboxEventRepository.save(OutboxEvent.of(
                "TRANSFER",
                transfer.getId(),
                "PIX_TRANSFER_FAILED",
                Map.of(
                        "transferId", transfer.getId(),
                        "reason", reason
                ),
                objectMapper
        ));
        return LiquidationResult.failure(reason);
    }

    private TransferRequest loadOrCreateTransfer(PixTransferRequestedEvent event) {
        return transferRequestRepository.findById(event.transferRequestId())
                .orElseGet(() -> transferRequestRepository.save(new TransferRequest(
                        event.transferRequestId(),
                        event.sourceAccountId(),
                        event.destinationPixKey(),
                        parseAmount(event.amount()),
                        event.currency(),
                        TransferStatus.PENDING,
                        null,
                        event.idempotencyKey(),
                        event.createdAt().toInstant(),
                        Instant.now()
                )));
    }

    private BigDecimal currentBalance(java.util.UUID accountId) {
        return ledgerRepository.findLastRunningBalance(accountId)
                .orElseGet(() -> ledgerRepository.calculateBalance(accountId).setScale(4, RoundingMode.UNNECESSARY));
    }

    private BigDecimal parseAmount(String value) {
        return new BigDecimal(value).setScale(4, RoundingMode.UNNECESSARY);
    }
}
