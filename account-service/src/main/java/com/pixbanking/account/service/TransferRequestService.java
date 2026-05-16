package com.pixbanking.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixbanking.account.domain.model.Account;
import com.pixbanking.account.domain.model.IdempotencyRecord;
import com.pixbanking.account.domain.model.OutboxEvent;
import com.pixbanking.account.domain.model.OutboxEventStatus;
import com.pixbanking.account.domain.model.OutboxEventType;
import com.pixbanking.account.domain.model.PixTransferRequestedEvent;
import com.pixbanking.account.domain.model.TransferRequest;
import com.pixbanking.account.domain.model.TransferRequestStatus;
import com.pixbanking.account.domain.repository.AccountRepository;
import com.pixbanking.account.domain.repository.IdempotencyRecordRepository;
import com.pixbanking.account.domain.repository.OutboxEventRepository;
import com.pixbanking.account.domain.repository.TransferRequestRepository;
import com.pixbanking.account.dto.CreateTransferRequest;
import com.pixbanking.account.dto.TransferCreatedResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TransferRequestService {

    private final AccountRepository accountRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public TransferRequestService(
            AccountRepository accountRepository,
            TransferRequestRepository transferRequestRepository,
            OutboxEventRepository outboxEventRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.transferRequestRepository = transferRequestRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransferCreatedResponse createTransferRequest(String idempotencyKey, CreateTransferRequest request) {
        Account account = accountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source account not found"));

        String normalizedPixKey = request.destinationPixKey().trim();
        String normalizedCurrency = request.currency().trim().toUpperCase();
        BigDecimal normalizedAmount;
        try {
            normalizedAmount = request.amount().setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must have up to 4 decimal places");
        }

        validateCurrency(normalizedCurrency, account.getCurrency());

        String requestHash = sha256(normalizeRequest(
                request.sourceAccountId(),
                normalizedPixKey,
                normalizedAmount,
                normalizedCurrency
        ));
        IdempotencyRecord existingRecord = idempotencyRecordRepository
                .findByAccountIdAndIdempotencyKey(request.sourceAccountId(), idempotencyKey)
                .orElse(null);

        if (existingRecord != null) {
            if (!existingRecord.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used with a different payload");
            }
            return deserialize(existingRecord.getResponseBody());
        }

        UUID transferRequestId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        TransferCreatedResponse response = new TransferCreatedResponse(transferRequestId, TransferRequestStatus.PENDING.name());

        TransferRequest transferRequest = new TransferRequest(
                transferRequestId,
                request.sourceAccountId(),
                normalizedPixKey,
                normalizedAmount,
                normalizedCurrency,
                TransferRequestStatus.PENDING,
                idempotencyKey,
                requestHash,
                now
        );

        transferRequestRepository.save(transferRequest);

        outboxEventRepository.save(new OutboxEvent(
                UUID.randomUUID(),
                transferRequestId,
                "TransferRequest",
                OutboxEventType.PIX_TRANSFER_REQUESTED,
                serialize(new PixTransferRequestedEvent(
                        transferRequestId,
                        request.sourceAccountId(),
                        normalizedPixKey,
                        normalizedAmount.toPlainString(),
                        normalizedCurrency,
                        idempotencyKey,
                        now
                )),
                OutboxEventStatus.PENDING,
                now,
                null
        ));

        idempotencyRecordRepository.save(new IdempotencyRecord(
                UUID.randomUUID(),
                request.sourceAccountId(),
                idempotencyKey,
                requestHash,
                transferRequestId,
                serialize(response),
                TransferRequestStatus.PENDING.name(),
                now
        ));

        return response;
    }

    private void validateCurrency(String requestCurrency, String accountCurrency) {
        if (!accountCurrency.equals(requestCurrency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency mismatch");
        }
    }

    private String normalizeRequest(UUID sourceAccountId, String destinationPixKey, BigDecimal amount, String currency) {
        return sourceAccountId + "|" +
                destinationPixKey + "|" +
                amount.toPlainString() + "|" +
                currency;
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize payload", e);
        }
    }

    private TransferCreatedResponse deserialize(String value) {
        try {
            return objectMapper.readValue(value, TransferCreatedResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize idempotent response", e);
        }
    }
}
