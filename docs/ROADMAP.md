# Roadmap

Este arquivo registra melhorias planejadas para evoluir o LedgerBank depois da Fase 1. A Fase 1 entrega o fluxo PIX assíncrono com ledger imutável, idempotência, outbox, testes E2E e CI.

## Fase 2 — Operação financeira e observabilidade

### QR Code dinâmico com `fund_reservations` e expiração

Objetivo: permitir cobranças PIX temporárias, com reserva de fundos antes da liquidação final.

Escopo previsto:
- Criar tabela `fund_reservations` com valor, conta origem, chave destino, status e `expires_at`.
- Reservar saldo de forma transacional antes de aceitar uma cobrança.
- Expirar reservas não confirmadas por job agendado.
- Garantir que reserva expirada não possa ser liquidada.
- Expor endpoints para criar, consultar e cancelar QR Codes dinâmicos.

Critério de aceite:
- Reserva válida bloqueia o valor até liquidação ou expiração.
- Reserva expirada libera o saldo.
- Duas reservas concorrentes não podem consumir o mesmo saldo.

### Reconciliação do ledger

Objetivo: detectar inconsistências contábeis automaticamente.

Escopo previsto:
- Criar job que valida `SUM(ledger_entries.amount)` por conta.
- Comparar o saldo calculado com o último `running_balance`.
- Registrar divergências em tabela de auditoria ou log estruturado.
- Expor métrica ou alerta para divergência encontrada.

Critério de aceite:
- Job identifica divergência artificial em teste.
- Job não altera dados financeiros automaticamente.
- Toda divergência gera trilha auditável.

### `expires_at` em `processed_messages` e job de limpeza

Objetivo: controlar crescimento da tabela de idempotência de mensagens consumidas.

Escopo previsto:
- Adicionar coluna `expires_at` em `processed_messages`.
- Definir TTL por configuração.
- Criar job de limpeza em lote.
- Preservar proteção contra duplicidade dentro da janela de retenção.

Critério de aceite:
- Mensagens antigas são removidas sem afetar mensagens recentes.
- O job é idempotente e seguro para execução recorrente.
- Retenção é configurável por serviço.

### Métricas com Micrometer + Grafana

Objetivo: tornar o comportamento do sistema observável em ambiente local e CI/CD.

Escopo previsto:
- Adicionar Spring Boot Actuator e Micrometer.
- Expor métricas de transferências por status.
- Medir publicação de outbox, consumo RabbitMQ e falhas de liquidação.
- Adicionar Prometheus e Grafana ao `docker-compose.yml`.
- Criar dashboard inicial para throughput, erros e latência.

Critério de aceite:
- `/actuator/prometheus` expõe métricas dos serviços.
- Grafana sobe localmente com dashboard provisionado.
- Falhas em workers aparecem como métrica/alerta.
