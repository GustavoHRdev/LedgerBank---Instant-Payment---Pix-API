package com.pixbanking.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixbanking.payment.domain.model.PixTransferRequestedEvent;
import com.pixbanking.payment.infra.messaging.OutboxPublisher;
import com.pixbanking.payment.service.TransferProcessingOrchestrator;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.rabbitmq.listener.simple.auto-startup=false"
)
class MessagingRecoveryTest {

    private static final String REQUEST_QUEUE = "payment.transfer-processor";
    private static final String COMPLETED_TEST_QUEUE = "payment.recovery.completed-test";
    private static final UUID PAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PAYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PIX_KEY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OPENING_TRANSFER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OPENING_ENTRY_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID TRANSFER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID OUTBOX_EVENT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String MESSAGE_ID = "recovery-message-001";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("payment_db")
            .withUsername("pixuser")
            .withPassword("pixpass");

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management")
            .withAdminUser("pixuser")
            .withAdminPassword("pixpass");

    @DynamicPropertySource
    static void serviceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CachingConnectionFactory cachingConnectionFactory;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransferProcessingOrchestrator orchestrator;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledTasks;

    @Autowired
    @Qualifier("pixEventsExchange")
    private DirectExchange pixEventsExchange;

    @BeforeEach
    void prepareDatabaseAndQueues() {
        scheduledTasks.getScheduledTasks().forEach(task -> task.cancel());
        reset(rabbitTemplate);
        listenerRegistry.getListenerContainers().forEach(container -> {
            if (container.isRunning()) {
                container.stop();
            }
        });
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
                "INSERT INTO transfer_requests (id, source_account_id, destination_pix_key, amount, currency, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 'bob@example.com', 100.0000, 'BRL', 'PENDING', 'recovery-test', now(), now())",
                TRANSFER_ID, PAYER_ID
        );

        rabbitAdmin.declareQueue(QueueBuilder.durable(COMPLETED_TEST_QUEUE).build());
        rabbitAdmin.declareBinding(BindingBuilder.bind(new Queue(COMPLETED_TEST_QUEUE, true))
                .to(pixEventsExchange)
                .with("transfer.completed"));
        rabbitAdmin.purgeQueue(REQUEST_QUEUE);
        rabbitAdmin.purgeQueue(COMPLETED_TEST_QUEUE);
    }

    @Test
    void requestWaitsInRabbitWhilePaymentListenerIsUnavailableAndProcessesAfterRecovery() throws Exception {
        String payload = objectMapper.writeValueAsString(requestedEvent());
        rabbitTemplate.send("", REQUEST_QUEUE, persistentMessage(MESSAGE_ID, payload));
        assertThat(rabbitAdmin.getQueueInfo(REQUEST_QUEUE).getMessageCount()).isEqualTo(1);

        listenerRegistry.getListenerContainers().forEach(container -> {
            if (!container.isRunning()) {
                container.start();
            }
        });

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(count("SELECT COUNT(*) FROM processed_messages WHERE message_id = ?", MESSAGE_ID)).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?", TRANSFER_ID)).isEqualTo(2);
        });
    }

    @Test
    void unackedDeliveryAfterDatabaseCommitIsRedeliveredWithoutRepeatingEffects() throws Exception {
        publishRequest(MESSAGE_ID);

        ConnectionFactory rawFactory = new ConnectionFactory();
        rawFactory.setHost(rabbitmq.getHost());
        rawFactory.setPort(rabbitmq.getAmqpPort());
        rawFactory.setUsername(rabbitmq.getAdminUsername());
        rawFactory.setPassword(rabbitmq.getAdminPassword());

        try (Connection connection = rawFactory.newConnection()) {
            Channel channel = connection.createChannel();
            GetResponse firstDelivery = channel.basicGet(REQUEST_QUEUE, false);
            assertThat(firstDelivery).isNotNull();
            assertThat(firstDelivery.getProps().getMessageId()).isEqualTo(MESSAGE_ID);

            orchestrator.process(MESSAGE_ID, requestedEvent());
            assertThat(count("SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?", TRANSFER_ID)).isEqualTo(2);

            // Closing with the message unacked models a consumer crash after the DB commit.
        }

        try (Connection redeliveryConnection = rawFactory.newConnection();
             Channel redeliveryChannel = redeliveryConnection.createChannel()) {
            GetResponse redelivery = redeliveryChannel.basicGet(REQUEST_QUEUE, false);
            assertThat(redelivery).isNotNull();
            assertThat(redelivery.getEnvelope().isRedeliver()).isTrue();

            orchestrator.process(redelivery.getProps().getMessageId(), objectMapper.readValue(
                    new String(redelivery.getBody(), StandardCharsets.UTF_8), PixTransferRequestedEvent.class));
            redeliveryChannel.basicAck(redelivery.getEnvelope().getDeliveryTag(), false);
        }

        assertTransferCommittedOnce();
    }

    @Test
    void pendingOutboxEventSurvivesRabbitConnectionFailureAndPublishesOnRetry() throws Exception {
        jdbc.update(
                "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, created_at) VALUES (?, 'TRANSFER', ?, 'PIX_TRANSFER_COMPLETED', ?, 'PENDING', now())",
                OUTBOX_EVENT_ID,
                TRANSFER_ID,
                objectMapper.writeValueAsString(Map.of("transferId", TRANSFER_ID.toString(), "result", "completed"))
        );

        AtomicBoolean brokerConnectionUnavailable = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (brokerConnectionUnavailable.get()) {
                throw new AmqpConnectException(new ConnectException("simulated RabbitMQ outage"));
            }
            return invocation.callRealMethod();
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class));

        assertThatThrownBy(outboxPublisher::publishPendingEvents).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id = ?", String.class, OUTBOX_EVENT_ID))
                .isEqualTo("PENDING");

        brokerConnectionUnavailable.set(false);

        assertThatCode(outboxPublisher::publishPendingEvents).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id = ?", String.class, OUTBOX_EVENT_ID))
                .isEqualTo("PUBLISHED");
        Message published = rabbitTemplate.receive(COMPLETED_TEST_QUEUE, 5000);
        assertThat(published).isNotNull();
        assertThat(published.getMessageProperties().getMessageId()).isEqualTo(OUTBOX_EVENT_ID.toString());
    }

    private PixTransferRequestedEvent requestedEvent() {
        return new PixTransferRequestedEvent(
                TRANSFER_ID,
                PAYER_ID,
                "bob@example.com",
                "100.0000",
                "BRL",
                "recovery-test",
                OffsetDateTime.now()
        );
    }

    private void publishRequest(String messageId) throws Exception {
        rabbitTemplate.send("", REQUEST_QUEUE, persistentMessage(
                messageId,
                objectMapper.writeValueAsString(requestedEvent())
        ));
    }

    private Message persistentMessage(String messageId, String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }

    private int count(String sql, Object parameter) {
        return jdbc.queryForObject(sql, Integer.class, parameter);
    }

    private void assertTransferCommittedOnce() {
        assertThat(count("SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ? AND entry_type = 'DEBIT'", TRANSFER_ID)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ? AND entry_type = 'CREDIT'", TRANSFER_ID)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'PIX_TRANSFER_COMPLETED'", TRANSFER_ID)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM processed_messages WHERE message_id = ?", MESSAGE_ID)).isEqualTo(1);
    }
}
