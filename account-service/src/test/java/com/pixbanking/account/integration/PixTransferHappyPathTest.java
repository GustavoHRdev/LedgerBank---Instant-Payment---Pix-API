package com.pixbanking.account.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class PixTransferHappyPathTest extends BaseIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("transferência válida deve completar e registrar ledger imutável")
    void deveCompletarTransferenciaERegistrarLedger() throws Exception {
        String postBody = postTransfer("happy-path-001", "150.00");
        JsonNode postResponse = objectMapper.readTree(postBody);

        assertThat(postResponse.get("status").asText()).isEqualTo("PENDING");
        String transferId = postResponse.get("transferRequestId").asText();

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    String statusBody = http.getForObject(transferStatusUrl(transferId), String.class);
                    JsonNode statusResponse = objectMapper.readTree(statusBody);
                    assertThat(statusResponse.get("status").asText()).isEqualTo("COMPLETED");
                    assertThat(statusResponse.get("completedAt").isNull()).isFalse();
                });

        List<Map<String, Object>> entries = paymentJdbc.queryForList(
                "SELECT entry_type, amount, running_balance FROM ledger_entries WHERE transfer_id = ?::uuid ORDER BY created_at",
                transferId
        );

        assertThat(entries).hasSize(2);

        Map<String, Object> debit = entries.stream()
                .filter(entry -> "DEBIT".equals(entry.get("entry_type")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> credit = entries.stream()
                .filter(entry -> "CREDIT".equals(entry.get("entry_type")))
                .findFirst()
                .orElseThrow();

        assertThat(debit.get("amount").toString()).isEqualTo("150.0000");
        assertThat(credit.get("amount").toString()).isEqualTo("150.0000");
        assertThat(debit.get("running_balance").toString()).isEqualTo("850.0000");
    }
}
