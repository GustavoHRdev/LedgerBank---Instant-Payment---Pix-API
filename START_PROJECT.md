# 🚀 Guia Rápido de Inicialização e Validação do Projeto

Este guia serve como um lembrete rápido de como subir o ambiente Docker e validar se todos os serviços (APIs, Banco de Dados e Mensageria) estão funcionando corretamente.

---

### 📅 1. Inicialização do Ambiente

1. Abra o **Docker Desktop** na sua máquina.
2. Acesse a pasta do projeto no seu terminal:
   ```bash
   cd "C:\Users\Win 10\IdeaProjects\LedgerBank---Instant-Payment---Pix-API"
   ```

---

### 🐳 2. Subir os Containers

* **Subir normalmente** (caso estejam apenas parados):
  ```bash
  docker compose up -d
  ```
* **Subir rebuildando** (caso você tenha alterado o código ou configurações):
  ```bash
  docker compose up --build -d
  ```

---

### 🔎 3. Verificar Status dos Serviços

Execute o comando abaixo para garantir que todos os containers estão de pé:
```bash
docker ps
```

**Containers esperados na lista:**
* `ledgerbank-account-service`
* `ledgerbank-payment-service`
* `ledgerbank-postgres`
* `ledgerbank-rabbitmq`

---

### 🦘 4. Painel do RabbitMQ

* **URL de Acesso:** [http://localhost:15672](http://localhost:15672)
* **Usuário:** `pixuser`
* **Senha:** `pixpass`

---

### 📊 5. Inspeção do Banco de Dados (PostgreSQL)

Para conectar e navegar no banco via terminal, siga esta sequência de comandos:

1. **Entrar no container:**
   ```bash
   docker exec -it ledgerbank-postgres bash
   ```
2. **Abrir o CLI do PostgreSQL:**
   ```bash
   psql -U pixuser -d postgres
   ```
3. **Comandos úteis dentro do Postgres:**
    * Listar os bancos: `\l`
    * Trocar para o banco da aplicação: `\c account_db`
    * Listar as tabelas: `\dt`
    * Sair do Postgres: `\q`

---

### 📖 6. Documentação das APIs (Swagger)

Use os links abaixo no navegador para testar os endpoints de cada serviço:

* **Account Service:** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
* **Payment Service:** [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)

---

### 7\. Verificar as APIs

**Account Service**

http://localhost:8081/swagger-ui.html

**Payment Service**

http://localhost:8082/swagger-ui.html