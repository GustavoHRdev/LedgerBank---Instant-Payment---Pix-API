package com.pixbanking.account.controller;

import com.pixbanking.account.dto.CreateTransferRequest;
import com.pixbanking.account.dto.TransferCreatedResponse;
import com.pixbanking.account.dto.TransferStatusResponse;
import com.pixbanking.account.service.TransferRequestService;
import com.pixbanking.account.service.TransferStatusQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/pix/transfers")
public class TransferRequestController {

    private final TransferRequestService transferRequestService;
    private final TransferStatusQueryService transferStatusQueryService;

    public TransferRequestController(
            TransferRequestService transferRequestService,
            TransferStatusQueryService transferStatusQueryService
    ) {
        this.transferRequestService = transferRequestService;
        this.transferStatusQueryService = transferStatusQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferCreatedResponse createTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        return transferRequestService.createTransferRequest(idempotencyKey, request);
    }

    @GetMapping("/{transferId}")
    public TransferStatusResponse getTransferStatus(@PathVariable UUID transferId) {
        return transferStatusQueryService.getTransferStatus(transferId);
    }
}
