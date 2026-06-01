package com.pixbanking.account.infra.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerBankOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LedgerBank — PIX API")
                        .version("1.0.0")
                        .description("""
                                Sistema de transferências PIX com ledger imutável,
                                idempotência real e processamento assíncrono via RabbitMQ.

                                **Fluxo:** POST /pix/transfers → 202 PENDING →
                                polling GET /pix/transfers/{id} → COMPLETED | FAILED
                                """)
                )
                .addServersItem(new Server()
                        .url("http://localhost:8081")
                        .description("Local")
                );
    }
}
