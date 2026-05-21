package com.pixbanking.account.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

class PixTransferEdgeCasesTest extends BaseIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("chave idempotente não deve duplicar lançamento no ledger")
    void idempotenciaNaoDeveDuplicarLedger() throws Exception {
        JsonNode firstResponse = objectMapper.readTree(postTransfer("idem-test-001", "150.00"));
        String transferId = firstResponse.get("transferRequestId").asText();

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    JsonNode statusResponse = objectMapper.readTree(http.getForObject(transferStatusUrl(transferId), String.class));
                    assertThat(statusResponse.get("status").asText()).isEqualTo("COMPLETED");
                });

        JsonNode secondResponse = objectMapper.readTree(postTransfer("idem-test-001", "150.00"));

        assertThat(secondResponse.get("transferRequestId").asText()).isEqualTo(transferId);
        assertThat(secondResponse.get("status").asText()).isEqualTo("COMPLETED");

        Integer ledgerEntries = paymentJdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?::uuid",
                Integer.class,
                transferId
        );
        assertThat(ledgerEntries).isEqualTo(2);
    }

    @Test
    @DisplayName("saldo insuficiente deve resultar em FAILED sem ledger entry")
    void saldoInsuficienteDeveResultarEmFailed() throws Exception {
        JsonNode postResponse = objectMapper.readTree(postTransfer("insufficient-balance-001", "99999.00"));
        String transferId = postResponse.get("transferRequestId").asText();

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    JsonNode statusResponse = objectMapper.readTree(http.getForObject(transferStatusUrl(transferId), String.class));
                    assertThat(statusResponse.get("status").asText()).isEqualTo("FAILED");
                    assertThat(statusResponse.get("failureReason").asText()).isEqualTo("INSUFFICIENT_BALANCE");
                });

        Integer ledgerEntries = paymentJdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?::uuid",
                Integer.class,
                transferId
        );
        assertThat(ledgerEntries).isEqualTo(0);
    }
}
