package com.pixbanking.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixbanking.account.domain.model.AuditEvent;
import com.pixbanking.account.domain.model.PixTransferCompletedEvent;
import com.pixbanking.account.domain.model.PixTransferFailedEvent;
import com.pixbanking.account.domain.model.ProcessedMessage;
import com.pixbanking.account.domain.model.TransferRequest;
import com.pixbanking.account.domain.repository.AuditEventRepository;
import com.pixbanking.account.domain.repository.ProcessedMessageRepository;
import com.pixbanking.account.domain.repository.TransferRequestRepository;
import com.pixbanking.account.infra.config.MessagingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class TransferNotificationService {

    private final TransferRequestRepository transferRequestRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final AuditEventRepository auditEventRepository;
    private final MessagingProperties messagingProperties;
    private final ObjectMapper objectMapper;

    public TransferNotificationService(
            TransferRequestRepository transferRequestRepository,
            ProcessedMessageRepository processedMessageRepository,
            AuditEventRepository auditEventRepository,
            MessagingProperties messagingProperties,
            ObjectMapper objectMapper
    ) {
        this.transferRequestRepository = transferRequestRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.auditEventRepository = auditEventRepository;
        this.messagingProperties = messagingProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleCompleted(String messageId, PixTransferCompletedEvent event) {
        if (processedMessageRepository.existsByMessageId(messageId)) {
            return;
        }

        TransferRequest transferRequest = transferRequestRepository.findById(event.transferId())
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + event.transferId()));

        String previousStatus = transferRequest.getStatus().name();
        transferRequest.markCompleted(event.completedAt());
        auditEventRepository.save(AuditEvent.of(
                "TRANSFER",
                event.transferId(),
                "STATUS_CHANGED",
                Map.of("from", previousStatus, "to", "COMPLETED"),
                objectMapper
        ));
        processedMessageRepository.save(ProcessedMessage.of(messageId, messagingProperties.notificationConsumerName()));
    }

    @Transactional
    public void handleFailed(String messageId, PixTransferFailedEvent event) {
        if (processedMessageRepository.existsByMessageId(messageId)) {
            return;
        }

        TransferRequest transferRequest = transferRequestRepository.findById(event.transferId())
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + event.transferId()));

        String previousStatus = transferRequest.getStatus().name();
        transferRequest.markFailed(event.reason(), event.failedAt());
        auditEventRepository.save(AuditEvent.of(
                "TRANSFER",
                event.transferId(),
                "STATUS_CHANGED",
                Map.of("from", previousStatus, "to", "FAILED", "reason", event.reason()),
                objectMapper
        ));
        processedMessageRepository.save(ProcessedMessage.of(messageId, messagingProperties.notificationConsumerName()));
    }
}
