package com.pixbanking.account.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("account_db")
            .withUsername("pixuser")
            .withPassword("pixpass");

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management")
            .withUser("pixuser", "pixpass");

    @Container
    static final GenericContainer<?> paymentService;

    static {
        postgres.start();
        rabbitmq.start();
        createDatabase("payment_db");
        org.testcontainers.Testcontainers.exposeHostPorts(postgres.getMappedPort(5432), rabbitmq.getMappedPort(5672));

        Path paymentServiceRoot = Path.of("..", "payment-service").toAbsolutePath().normalize();
        ImageFromDockerfile paymentServiceImage = new ImageFromDockerfile("ledgerbank-payment-service-test", false)
                .withFileFromPath("Dockerfile", paymentServiceRoot.resolve("Dockerfile"))
                .withFileFromPath("pom.xml", paymentServiceRoot.resolve("pom.xml"))
                .withFileFromPath("src", paymentServiceRoot.resolve("src"));

        paymentService = new GenericContainer<>(paymentServiceImage)
                .withAccessToHost(true)
                .withExposedPorts(8082)
                .withEnv("DB_HOST", "host.testcontainers.internal")
                .withEnv("DB_PORT", String.valueOf(postgres.getMappedPort(5432)))
                .withEnv("DB_NAME", "payment_db")
                .withEnv("DB_USERNAME", postgres.getUsername())
                .withEnv("DB_PASSWORD", postgres.getPassword())
                .withEnv("RABBITMQ_HOST", "host.testcontainers.internal")
                .withEnv("RABBITMQ_PORT", String.valueOf(rabbitmq.getAmqpPort()))
                .withEnv("RABBITMQ_USERNAME", "pixuser")
                .withEnv("RABBITMQ_PASSWORD", "pixpass")
                .waitingFor(Wait.forLogMessage(".*Started PaymentServiceApplication.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(4)));

        paymentService.start();
    }

    private static void createDatabase(String databaseName) {
        String adminUrl = postgres.getJdbcUrl().replace("/account_db", "/postgres");
        try (Connection connection = DriverManager.getConnection(adminUrl, postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        } catch (Exception ex) {
            if (!ex.getMessage().contains("already exists")) {
                throw new IllegalStateException("Could not create test database " + databaseName, ex);
            }
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "pixuser");
        registry.add("spring.rabbitmq.password", () -> "pixpass");
    }

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected DataSource dataSource;

    protected JdbcTemplate accountJdbc;
    protected JdbcTemplate paymentJdbc;

    @BeforeEach
    void cleanUp() {
        this.accountJdbc = new JdbcTemplate(dataSource);
        this.paymentJdbc = new JdbcTemplate(paymentDataSource());

        purgeRabbitArtifacts();
        resetAccountDatabase();
        resetPaymentDatabase();
    }

    protected String transferUrl() {
        return "/pix/transfers";
    }

    protected String transferStatusUrl(String transferId) {
        return "/pix/transfers/" + transferId;
    }

    protected String postTransfer(String idempotencyKey, String amount) {
        String payload = """
                {
                  "sourceAccountId": "11111111-1111-1111-1111-111111111111",
                  "destinationPixKey": "bob@example.com",
                  "amount": "%s",
                  "currency": "BRL"
                }
                """.formatted(amount);

        var headers = new org.springframework.http.HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        var response = http.exchange(
                transferUrl(),
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(payload, headers),
                String.class
        );

        return response.getBody();
    }

    private DataSource paymentDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl().replace("/account_db", "/payment_db"));
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    private void purgeRabbitArtifacts() {
        accountJdbc.execute("DELETE FROM processed_messages");
        paymentJdbcSafeDelete();
    }

    private void paymentJdbcSafeDelete() {
        if (paymentJdbc == null) {
            return;
        }
        paymentJdbc.execute("DELETE FROM processed_messages");
    }

    private void resetAccountDatabase() {
        accountJdbc.execute("TRUNCATE TABLE audit_events, processed_messages, idempotency_records, outbox_events, transfer_requests, ledger_entries RESTART IDENTITY CASCADE");
        accountJdbc.update(
                "INSERT INTO accounts (id, owner_name, currency, created_at) VALUES (?::uuid, ?, ?, now()) ON CONFLICT (id) DO NOTHING",
                "11111111-1111-1111-1111-111111111111", "Alice Payer", "BRL"
        );
    }

    private void resetPaymentDatabase() {
        paymentJdbc.execute("TRUNCATE TABLE processed_messages, outbox_events, ledger_entries, transfer_requests RESTART IDENTITY CASCADE");
        paymentJdbc.update(
                "INSERT INTO accounts (id, owner_name, status, currency, created_at) VALUES (?::uuid, ?, ?, ?, now()) ON CONFLICT (id) DO NOTHING",
                "11111111-1111-1111-1111-111111111111", "Alice Payer", "ACTIVE", "BRL"
        );
        paymentJdbc.update(
                "INSERT INTO accounts (id, owner_name, status, currency, created_at) VALUES (?::uuid, ?, ?, ?, now()) ON CONFLICT (id) DO NOTHING",
                "22222222-2222-2222-2222-222222222222", "Bob Payee", "ACTIVE", "BRL"
        );
        paymentJdbc.update(
                "INSERT INTO pix_keys (id, account_id, value, active, created_at) VALUES (?::uuid, ?::uuid, ?, true, now()) ON CONFLICT (value) DO NOTHING",
                "33333333-3333-3333-3333-333333333333", "22222222-2222-2222-2222-222222222222", "bob@example.com"
        );
        paymentJdbc.update(
                """
                INSERT INTO transfer_requests
                    (id, source_account_id, destination_pix_key, amount, currency, status, failure_reason, idempotency_key, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                "44444444-4444-4444-4444-444444444444",
                "11111111-1111-1111-1111-111111111111",
                "bootstrap",
                new java.math.BigDecimal("1000.0000"),
                "BRL",
                "COMPLETED",
                null,
                "bootstrap-seed"
        );
        paymentJdbc.update(
                """
                INSERT INTO ledger_entries
                    (id, account_id, transfer_id, entry_type, amount, running_balance, created_at)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, now())
                """,
                "55555555-5555-5555-5555-555555555555",
                "11111111-1111-1111-1111-111111111111",
                "44444444-4444-4444-4444-444444444444",
                "CREDIT",
                new java.math.BigDecimal("1000.0000"),
                new java.math.BigDecimal("1000.0000")
        );
    }
}
