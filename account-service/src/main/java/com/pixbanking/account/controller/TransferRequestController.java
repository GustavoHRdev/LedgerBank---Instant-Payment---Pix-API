package com.pixbanking.account.controller;

import com.pixbanking.account.dto.CreateTransferRequest;
import com.pixbanking.account.dto.TransferCreatedResponse;
import com.pixbanking.account.dto.TransferStatusResponse;
import com.pixbanking.account.service.TransferRequestService;
import com.pixbanking.account.service.TransferStatusQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "PIX Transfers", description = "Intake e consulta de transferências PIX")
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
    @Operation(
            summary = "Solicitar transferência PIX",
            description = """
                    Cria uma transferência assíncrona. O processamento ocorre via worker —
                    use o transferId retornado para consultar o status final.
                    O header Idempotency-Key garante que requisições repetidas não
                    geram cobranças duplicadas.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Transferência aceita e enfileirada"),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key já usada com body diferente"),
            @ApiResponse(responseCode = "422", description = "Dados inválidos")
    })
    public TransferCreatedResponse createTransfer(
            @RequestHeader("Idempotency-Key")
            @Parameter(description = "UUID único gerado pelo cliente para garantir idempotência")
            String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        return transferRequestService.createTransferRequest(idempotencyKey, request);
    }

    @GetMapping("/{transferId}")
    @Operation(
            summary = "Consultar status da transferência",
            description = "Retorna o estado atual. Faça polling até status COMPLETED ou FAILED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retornado"),
            @ApiResponse(responseCode = "404", description = "Transferência não encontrada")
    })
    public TransferStatusResponse getTransferStatus(@PathVariable UUID transferId) {
        return transferStatusQueryService.getTransferStatus(transferId);
    }
}
