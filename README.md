# LedgerBank — Instant Payment PIX API

Sistema bancário simplificado com transferências PIX assíncronas, modelagem financeira imutável e garantias de consistência distribuída.

---

## Por que este projeto existe

A maioria dos projetos de portfólio que implementam transferências faz:

```java
account.setSaldo(account.getSaldo() - valor); // ❌
repository.save(account);
```

Isso quebra em produção. Race conditions, saldo negativo indevido, dinheiro que some entre serviços.

Este projeto foi construído para demonstrar como um sistema financeiro real funciona:
- Saldo nunca é uma coluna — é calculado do ledger
- Dinheiro nunca some entre serviços — Outbox Pattern garante entrega
- O mesmo request nunca é processado duas vezes — idempotência real
- Falhas são cidadãos de primeira classe — estados explícitos e DLQ

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                        Client                               │
└──────────────────────────┬──────────────────────────────────┘
                           │ POST /pix/transfers
                           │ Idempotency-Key header
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   account-service :8081                     │
│                                                             │
│  ┌─────────────┐   ┌──────────────┐   ┌─────────────────┐  │
│  │  Controller │──▶│TransferRequest│──▶│  OutboxPublisher│  │
│  └─────────────┘   │   Service    │   │  (@Scheduled)   │  │
│                    └──────────────┘   └────────┬────────┘  │
│                    (mesma transação)            │           │
│  ┌──────────────────────────────────┐           │           │
│  │  transfer_requests               │           │           │
│  │  idempotency_records             │           │           │
│  │  outbox_events ◀─────────────────┘           │           │
│  └──────────────────────────────────┘           │           │
└─────────────────────────────────────────────────┼───────────┘
                                                  │ pix.events
                                                  │ transfer.requested
                                                  ▼
                                        ┌─────────────────┐
                                        │    RabbitMQ     │
                                        │  pix.events     │
                                        │  (topic)        │
                                        └────────┬────────┘
                                                 │
                           ┌─────────────────────┼──────────────────────┐
                           │                     │                      │
                    transfer.requested    transfer.completed      transfer.failed
                           │                     │                      │
                           ▼                     ▼                      ▼
┌──────────────────────────────┐   ┌─────────────────────────────────────────┐
│     payment-service :8082    │   │            account-service              │
│                              │   │                                         │
│  PixTransferRequestedConsumer│   │  PixTransferCompletedConsumer           │
│         │                    │   │  PixTransferFailedConsumer              │
│         ▼                    │   │         │                               │
│  TransferLiquidationService  │   │         ▼                               │
│         │                    │   │  TransferNotificationService            │
│  SELECT FOR UPDATE (lock)    │   │  (atualiza status + audit_events)       │
│  INSERT DEBIT (ledger)       │   └─────────────────────────────────────────┘
│  INSERT CREDIT (ledger)      │
│  outbox_events ──────────────┼──▶ pix.events / transfer.completed
└──────────────────────────────┘
```

---

## Decisões técnicas

### Ledger imutável — por que não ter coluna `saldo`

```sql
-- ❌ O que a maioria faz
UPDATE accounts SET saldo = saldo - 150.00 WHERE id = ?

-- ✅ O que este projeto faz
INSERT INTO ledger_entries (account_id, entry_type, amount, running_balance)
VALUES (?, 'DEBIT', 150.00, 850.00);
```

Com coluna de saldo, duas transferências simultâneas leem `1000.00`, ambas subtraem `150.00`, ambas fazem `UPDATE saldo = 850.00`. Resultado: R$150 desapareceu.

Com ledger, saldo é `SUM` de todas as entradas. O `SELECT ... FOR UPDATE` garante que apenas uma transação por vez calcula e insere. Toda movimentação é rastreável desde o início da conta.

### `BigDecimal` — por que não `double`

```java
// ❌ ponto flutuante binário
double a = 0.1 + 0.2; // = 0.30000000000000004

// ✅ decimal exato
BigDecimal a = new BigDecimal("0.1").add(new BigDecimal("0.2")); // = 0.3
```

Em dinheiro, imprecisão não existe. No banco: `DECIMAL(19,4)`. Na API: `amount` trafega como `String` JSON para evitar que o runtime deserialize como `double` antes de chegar no código.

### Outbox Pattern — por que não publicar direto no RabbitMQ

```
❌ Sem Outbox:
  1. commit no banco           ← processo morre aqui
  2. rabbitTemplate.send()     ← nunca executa
  resultado: banco tem a transferência, fila não tem o evento

✅ Com Outbox:
  1. INSERT outbox_events }
  2. INSERT transfer_request } mesma transação — ou tudo ou nada
  3. @Scheduled lê outbox e publica na fila
  resultado: banco e fila sempre sincronizados
```

### Idempotência — por que `idempotency_records` é tabela separada

Se a checagem e a criação da transferência fossem na mesma tabela, duas requisições simultâneas com a mesma key poderiam passar pela checagem antes de qualquer uma ter feito INSERT. Com tabela separada e `INSERT ... ON CONFLICT DO NOTHING`, a operação é atômica.

### Estados de transação

```
PENDING ──▶ PROCESSING ──▶ COMPLETED
                       └──▶ FAILED      (dinheiro nunca saiu)
                       └──▶ REVERSED    (débito ocorreu, crédito falhou)
```

`FAILED` e `REVERSED` são estados diferentes por uma razão contábil: `FAILED` significa que nenhuma entrada foi feita no ledger. `REVERSED` significa que há um `DEBIT` e um `REVERSAL_CREDIT` registrados — o efeito econômico é zero, mas a trilha existe.

---

## Stack

| Camada | Tecnologia | Por quê |
|---|---|---|
| Runtime | Java 21 + Virtual Threads | Project Loom — alta concorrência sem thread pool blocking |
| Framework | Spring Boot 3.5 | Ecossistema maduro, integração nativa com RabbitMQ e JPA |
| Banco | PostgreSQL 16 | ACID real, `SELECT FOR UPDATE`, `DECIMAL(19,4)` |
| Cache/Idem | Redis | Idempotência com TTL, sem poluir o banco principal |
| Fila | RabbitMQ 3.13 | Topic exchange, DLQ nativa, management UI |
| Migrations | Flyway | Histórico versionado, sem surpresa entre ambientes |
| Testes | Testcontainers + Awaitility | Postgres e RabbitMQ reais no teste, sem mock de infraestrutura |
| Container | Docker + Docker Compose | Stack completa com um comando |

---

## Como rodar

**Pré-requisitos:** Docker Desktop, JDK 21

```bash
# clona o repositório
git clone https://github.com/GustavoHRdev/LedgerBank---Instant-Payment---Pix-API.git
cd "LedgerBank---Instant-Payment---Pix-API"

# sobe toda a stack
docker compose up --build
```

Aguarda as linhas:
```
account-service | Started AccountServiceApplication
payment-service | Started PaymentServiceApplication
```

---

## Testando o fluxo

### 1. Transferência PIX (happy path)

```bash
curl -X POST http://localhost:8081/pix/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: meu-uuid-unico" \
  -d '{
    "sourceAccountId": "11111111-1111-1111-1111-111111111111",
    "destinationPixKey": "bob@example.com",
    "amount": "150.00",
    "currency": "BRL"
  }'
```

Resposta imediata (`202 Accepted`):
```json
{ "transferRequestId": "uuid", "status": "PENDING" }
```

### 2. Polling de status

```bash
curl http://localhost:8081/pix/transfers/{transferRequestId}
```

Após ~3 segundos:
```json
{
  "transferId": "uuid",
  "status": "COMPLETED",
  "amount": "150.0000",
  "completedAt": "2026-05-21T03:20:12.542237Z"
}
```

### 3. Verificar ledger (prova de imutabilidade)

```bash
docker compose exec postgres psql -U pixuser -d payment_db \
  -c "SELECT account_id, entry_type, amount, running_balance FROM ledger_entries ORDER BY created_at;"
```

```
 account_id                            | entry_type | amount    | running_balance
---------------------------------------+------------+-----------+----------------
 11111111-1111-1111-1111-111111111111  | CREDIT     | 1000.0000 | 1000.0000   ← seed
 11111111-1111-1111-1111-111111111111  | DEBIT      |  150.0000 |  850.0000   ← transferência
 22222222-2222-2222-2222-222222222222  | CREDIT     |  150.0000 |  150.0000   ← recebimento
```

### 4. Idempotência (mesmo request, sem duplicação)

```bash
# segundo request com a mesma Idempotency-Key
curl -X POST http://localhost:8081/pix/transfers \
  -H "Idempotency-Key: meu-uuid-unico" \
  ...

# ledger permanece com 3 entradas — não duplicou
docker compose exec postgres psql -U pixuser -d payment_db \
  -c "SELECT COUNT(*) FROM ledger_entries;"
# count = 3
```

### 5. Saldo insuficiente

```bash
curl -X POST http://localhost:8081/pix/transfers \
  -H "Idempotency-Key: outro-uuid" \
  -d '{ ..., "amount": "99999.00" }'

# polling retorna:
# { "status": "FAILED", "failureReason": "INSUFFICIENT_BALANCE" }
```

---

## Rodando os testes de integração

```bash
cd account-service
mvn test
```

```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Total time: ~1m 18s
```

Os testes sobem PostgreSQL e RabbitMQ reais via Testcontainers e exercitam o fluxo ponta a ponta incluindo o `payment-service` containerizado.

---

## Estrutura do projeto

```
├── account-service/        # intake de transferências, idempotência, status
│   ├── src/main/
│   │   ├── controller/     # endpoints REST
│   │   ├── service/        # regras de negócio
│   │   ├── domain/         # entidades e repositórios
│   │   └── infra/          # RabbitMQ, scheduling, segurança
│   └── src/test/
│       └── integration/    # Testcontainers
│
├── payment-service/        # liquidação transacional, ledger, worker
│   ├── src/main/
│   │   ├── worker/         # consumidor RabbitMQ
│   │   ├── service/        # lock pessimista, DEBIT/CREDIT
│   │   └── domain/         # LedgerEntry, TransferRequest
│   └── src/main/resources/
│       └── db/migration/   # Flyway
│
├── docker-compose.yml      # stack completa local
└── docker/
    └── postgres/
        └── init-multiple-dbs.sh
```

---

## Filas RabbitMQ

| Fila | Produzida por | Consumida por |
|---|---|---|
| `payment.transfer-processor` | account-service | payment-service |
| `account.notification-sender.completed` | payment-service | account-service |
| `account.notification-sender.failed` | payment-service | account-service |
| `pix.dead-letter` | RabbitMQ (após retries) | investigação manual |

Painel de administração: `http://localhost:15672` (pixuser/pixpass)

---

## Próximas evoluções (fase 2)

- `balance_snapshots` — performance de saldo em contas antigas
- QR Code dinâmico com `fund_reservations` e expiração
- Reconciliação — job que valida `SUM(ledger)` contra `running_balance`
- Métricas com Micrometer + Grafana
- `expires_at` em `processed_messages` + job de limpeza