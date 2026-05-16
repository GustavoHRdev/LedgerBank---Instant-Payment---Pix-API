package com.pixbanking.payment.service;

import com.pixbanking.payment.domain.model.PixTransferRequestedEvent;
import com.pixbanking.payment.domain.model.ProcessedMessage;
import com.pixbanking.payment.domain.repository.ProcessedMessageRepository;
import com.pixbanking.payment.infra.config.MessagingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferProcessingOrchestrator {

    private final ProcessedMessageRepository processedMessageRepository;
    private final TransferLiquidationService transferLiquidationService;
    private final MessagingProperties messagingProperties;

    public TransferProcessingOrchestrator(
            ProcessedMessageRepository processedMessageRepository,
            TransferLiquidationService transferLiquidationService,
            MessagingProperties messagingProperties
    ) {
        this.processedMessageRepository = processedMessageRepository;
        this.transferLiquidationService = transferLiquidationService;
        this.messagingProperties = messagingProperties;
    }

    @Transactional
    public void process(String messageId, PixTransferRequestedEvent event) {
        if (processedMessageRepository.existsByMessageId(messageId)) {
            return;
        }

        try {
            transferLiquidationService.liquidate(event);
            markProcessed(messageId);
        } catch (BusinessRuleException ex) {
            transferLiquidationService.failTransfer(event, ex.getCode());
            markProcessed(messageId);
        }
    }

    private void markProcessed(String messageId) {
        processedMessageRepository.save(ProcessedMessage.of(messageId, messagingProperties.consumerName()));
    }
}
