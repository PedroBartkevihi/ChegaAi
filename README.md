# 📍 ChegAê — Conexões Reais e Seguras

O **ChegAê** é uma plataforma que conecta pessoas para a realização de atividades e rolés presenciais com foco em segurança, controle de reputação e organização inteligente de vagas. 

Este repositório está organizado como um **Monorepo**, facilitando o desenvolvimento integrado do Front-end (React) e do Back-end (Spring Boot), além de centralizar as definições de infraestrutura (Docker, PostgreSQL, GraphDB).

---

## 📂 Estrutura do Repositório

```text
├── frontend/                  # Aplicação Single Page Application (SPA) em React + Vite.
├── backend/                   # API RESTful construída em Java com Spring Boot.
├── database/
│   ├── postgres/migrations/   # Scripts SQL de DDL e DML para o PostgreSQL.
│   └── graphdb/               # Consultas SPARQL e scripts RDF para o GraphDB.
├── infra/                     # Docker Compose e configurações de infraestrutura local.
├── docs/                      # Documentação técnica e histórico do projeto.
├── config/                    # Configurações compartilhadas de ambiente.
|
```

---

## 🚀 Requisitos e Tecnologias

### Tecnologias Utilizadas
- **Front-end**: React 19, Vite, Lucide Icons, Vanilla CSS com design responsivo e moderno.
- **Back-end**: Java 17, Spring Boot 3, Spring Data JPA, Spring Security (CORS liberado para o frontend).
- **Bancos de Dados**:
  - **PostgreSQL** (Persistência relacional de usuários, eventos, comentários, inscrições).
  - **GraphDB** (Mapeamento semântico e sistema de recomendações de rolés).
- **Infraestrutura**: Docker & Docker Compose.

---

## 🛠️ Como Iniciar o Projeto Localmente

Siga o passo a passo abaixo para ligar todos os serviços do ChegAê na sua máquina local:

### Passo 1: Subir o Banco de Dados (PostgreSQL)
Certifique-se de ter o Docker e Docker Compose instalados. Execute os comandos abaixo para iniciar o container do PostgreSQL:

```bash
# Caso precise de permissões de administrador (Linux):
sudo docker compose -f infra/docker-compose.yml up -d postgres

# Para checar o status e ver se está ativo:
sudo docker compose -f infra/docker-compose.yml ps
```
*O PostgreSQL estará disponível na porta padrão **5432**.*

---

### Passo 2: Configurar e Rodar o Back-end (Spring Boot)
1. Navegue até a pasta do backend:
   ```bash
   cd backend
   ```
2. Compile e inicialize o servidor com o Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```
   *(ou apenas `mvn spring-boot:run` se possuir o Maven instalado globalmente).*

3. **Carga Inicial Automática (Seed)**: 
   Ao iniciar, o serviço `DataSeederService` do backend irá popular o banco de dados automaticamente caso ele esteja vazio, inserindo categorias, localizações, rolés demonstrativos e vários usuários para testes.

*O servidor do backend estará ativo em **`http://localhost:8080`**.*

---

### Passo 3: Rodar o Front-end (React)
1. Abra um novo terminal e navegue para a pasta do frontend:
   ```bash
   cd frontend
   ```
2. Instale as dependências necessárias:
   ```bash
   npm install
   ```
3. Inicie o servidor de desenvolvimento:
   ```bash
   npm run dev
   ```
4. Acesse a aplicação no seu navegador:
   **`http://localhost:5173`**

*Nota: O frontend está configurado com um proxy transparente no Vite. Todas as requisições para `/api` são redirecionadas automaticamente para `http://localhost:8080`.*

---

## 👥 Contas Demonstrativas para Testes

O sistema possui uma gama de usuários simulando diferentes perfis e níveis de reputação. Faça o login utilizando os e-mails abaixo na tela inicial:

| E-mail | Tipo de Conta | Nível de Reputação | Comportamento Esperado |
| :--- | :--- | :--- | :--- |
| **`pedro@chegae.dev`** | Embaixador | **4.9** | Acesso total. Pode criar rolés de destaque com limite de até **50 participantes**. |
| **`caio@chegae.dev`** | Embaixador | **5.0** | Acesso total. Pode criar rolés de destaque com limite de até **50 participantes**. |
| **`marcos@chegae.dev`** | Embaixador | **5.0** | Acesso total. Pode criar rolés de destaque com limite de até **50 participantes**. |
| **`baixa@chegae.dev`** | Participante | **2.5** | **Reputação Baixa**. Bloqueado para inscrição de novos eventos devido à reputação abaixo do limite de 3.0. |
| **`moderador@chegae.dev`**| Administrador | **5.0** | Acesso ao **Painel de Moderação** para aprovar, arquivar ou suspender rolés denunciados. |

---
