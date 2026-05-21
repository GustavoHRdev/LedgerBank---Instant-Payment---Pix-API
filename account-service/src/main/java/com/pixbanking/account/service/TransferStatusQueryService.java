package com.pixbanking.account.service;

import com.pixbanking.account.domain.model.TransferRequest;
import com.pixbanking.account.domain.repository.TransferRequestRepository;
import com.pixbanking.account.dto.TransferStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class TransferStatusQueryService {

    private final TransferRequestRepository transferRequestRepository;

    public TransferStatusQueryService(TransferRequestRepository transferRequestRepository) {
        this.transferRequestRepository = transferRequestRepository;
    }

    public TransferStatusResponse getTransferStatus(UUID transferId) {
        TransferRequest transfer = transferRequestRepository.findById(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found"));

        return new TransferStatusResponse(
                transfer.getId(),
                transfer.getStatus().name(),
                transfer.getAmount().toPlainString(),
                transfer.getDestinationPixKey(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt(),
                transfer.getFailureReason()
        );
    }
}
