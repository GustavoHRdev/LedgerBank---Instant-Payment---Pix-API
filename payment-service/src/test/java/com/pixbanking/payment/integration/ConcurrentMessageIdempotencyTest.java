package com.pixbanking.payment.integration;

import com.pixbanking.payment.domain.model.PixTransferRequestedEvent;
import com.pixbanking.payment.infra.messaging.OutboxPublisher;
import com.pixbanking.payment.service.TransferProcessingOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.rabbitmq.listener.simple.auto-startup=false"
)
class ConcurrentMessageIdempotencyTest {

    private static final UUID PAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PAYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PIX_KEY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OPENING_TRANSFER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TRANSFER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID OPENING_ENTRY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String MESSAGE_ID = "concurrent-message-id-001";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("payment_db")
            .withUsername("pixuser")
            .withPassword("pixpass");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransferProcessingOrchestrator orchestrator;

    @MockitoBean
    private OutboxPublisher outboxPublisher;

    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE processed_messages, outbox_events, ledger_entries, transfer_requests, pix_keys, accounts CASCADE");

        jdbc.update(
                "INSERT INTO accounts (id, owner_name, status, currency, created_at) VALUES (?, ?, 'ACTIVE', 'BRL', now()), (?, ?, 'ACTIVE', 'BRL', now())",
                PAYER_ID, "Alice Payer", PAYEE_ID, "Bob Payee"
        );
        jdbc.update(
                "INSERT INTO pix_keys (id, account_id, value, active, created_at) VALUES (?, ?, ?, true, now())",
                PIX_KEY_ID, PAYEE_ID, "bob@example.com"
        );
        jdbc.update(
                "INSERT INTO transfer_requests (id, source_account_id, destination_pix_key, amount, currency, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 'opening', 1000.0000, 'BRL', 'COMPLETED', 'opening', now(), now())",
                OPENING_TRANSFER_ID, PAYER_ID
        );
        jdbc.update(
                "INSERT INTO ledger_entries (id, account_id, transfer_id, entry_type, amount, running_balance, created_at) VALUES (?, ?, ?, 'CREDIT', 1000.0000, 1000.0000, now())",
                OPENING_ENTRY_ID, PAYER_ID, OPENING_TRANSFER_ID
        );
        jdbc.update(
                "INSERT INTO transfer_requests (id, source_account_id, destination_pix_key, amount, currency, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 'bob@example.com', 100.0000, 'BRL', 'PENDING', 'concurrent-test', now(), now())",
                TRANSFER_ID, PAYER_ID
        );
    }

    @Test
    void concurrentCallsWithSameMessageIdCommitOnlyOneFinancialEffect() throws Exception {
        var event = new PixTransferRequestedEvent(
                TRANSFER_ID,
                PAYER_ID,
                "bob@example.com",
                "100.0000",
                "BRL",
                "concurrent-test",
                OffsetDateTime.now()
        );

        boolean bothTransactionsReachedDatabaseLock;
        RuntimeException firstOutcome;
        RuntimeException secondOutcome;
        try (Connection blockerConnection = dataSource.getConnection();
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            blockerConnection.setAutoCommit(false);
            try (PreparedStatement lockPayer = blockerConnection.prepareStatement(
                    "SELECT id FROM accounts WHERE id = ? FOR UPDATE")) {
                lockPayer.setObject(1, PAYER_ID);
                lockPayer.executeQuery().close();
            }

            Future<RuntimeException> first = executor.submit(() -> processAndCaptureFailure(event));
            Future<RuntimeException> second = executor.submit(() -> processAndCaptureFailure(event));

            bothTransactionsReachedDatabaseLock = awaitConcurrentLockWaits(10, TimeUnit.SECONDS);
            blockerConnection.commit();
            firstOutcome = first.get(20, TimeUnit.SECONDS);
            secondOutcome = second.get(20, TimeUnit.SECONDS);
        }

        assertThat(bothTransactionsReachedDatabaseLock)
                .as("both transactions must pass the idempotency check and reach PostgreSQL before either is released")
                .isTrue();
        var outcomes = Arrays.asList(firstOutcome, secondOutcome);
        assertThat(outcomes.stream().filter(outcome -> outcome == null)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> outcome != null)).hasSize(1);
        RuntimeException losingOutcome = outcomes.stream().filter(outcome -> outcome != null).findFirst().orElseThrow();
        assertThat(exceptionChain(losingOutcome)).contains("uq_processed_messages_message_id");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ? AND entry_type = 'DEBIT'",
                Integer.class,
                TRANSFER_ID
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ? AND entry_type = 'CREDIT'",
                Integer.class,
                TRANSFER_ID
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'PIX_TRANSFER_COMPLETED'",
                Integer.class,
                TRANSFER_ID
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_messages WHERE message_id = ?",
                Integer.class,
                MESSAGE_ID
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM transfer_requests WHERE id = ?",
                String.class,
                TRANSFER_ID
        )).isEqualTo("COMPLETED");
    }

    private RuntimeException processAndCaptureFailure(PixTransferRequestedEvent event) {
        try {
            orchestrator.process(MESSAGE_ID, event);
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private boolean awaitConcurrentLockWaits(long timeout, TimeUnit timeUnit) throws InterruptedException {
        long deadline = System.nanoTime() + timeUnit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            Integer waitingTransactions = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE pid <> pg_backend_pid() AND wait_event_type = 'Lock'",
                    Integer.class
            );
            if (waitingTransactions != null && waitingTransactions >= 2) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private String exceptionChain(Throwable exception) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            messages.append(cause.getClass().getName()).append(':').append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
