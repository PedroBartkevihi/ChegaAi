# ChegAê 🚀

O **ChegAê** é uma plataforma inovadora para conectar pessoas interessadas em realizar atividades presenciais (rolés) na faculdade ou na cidade, com um forte foco em um sistema orgânico de **reputação**, **segurança**, controle rigoroso de **vagas** e gerenciamento inteligente de **lista de espera**.

Seja para marcar um futebol de sexta, organizar um grupo de estudos ou adotar uma trilha no fim de semana, o ChegAê garante que os organizadores sejam confiáveis e que os participantes sejam comprometidos.

---

## 🛠️ Tecnologias Utilizadas

A arquitetura do projeto foi construída seguindo o modelo cliente-servidor, separando claramente as responsabilidades do frontend e backend, e validada com sólidas estratégias de testes automatizados e medição de desempenho.

### Backend (API REST)
- **Java 17** & **Spring Boot 3.3**
- **Spring Data JPA** & **Hibernate**
- Banco de Dados: **PostgreSQL**
- Migrations: **Flyway**
- Arquitetura MVC com Repository Pattern

### Frontend (Web App)
- **React 18** & **Vite**
- Componentização Funcional e Hooks
- Estilização Responsiva e Dinâmica

### Qualidade e Confiabilidade (QA)
- Testes Ponta-a-Ponta (E2E): **Playwright**
- Testes de Carga e Stress: **k6**
- Ambiente conteinerizado: **Docker** & **Docker Compose**

---

## 🎯 Principais Regras de Negócio Implementadas

1. **Reputação (Índice de Honra)**: Usuários que cancelam presença de última hora perdem pontos. Usuários com honra baixa (abaixo de 2.0) são **impedidos de criar novos eventos**, protegendo a plataforma.
2. **Avaliações Obrigatórias**: Não é possível entrar em um novo rolé se você deixou de avaliar os participantes de um rolé passado.
3. **Fila de Espera Automática (FIFO)**: Quando um evento atinge a capacidade máxima, novos inscritos caem automaticamente em uma fila de espera ordenada por ordem de chegada.
4. **Fórum de Comentários Restrito**: Apenas participantes confirmados ou na lista de espera podem interagir nos comentários do evento, evitando spam de não-inscritos.

---

## 🚀 Como Executar o Projeto Localmente

### 1. Requisitos Prévios
- [Node.js](https://nodejs.org/en/) (v20+)
- [Java JDK 17+](https://adoptium.net/)
- [Maven](https://maven.apache.org/) (v3.9+)
- [Docker](https://www.docker.com/) e Docker Compose

### 2. Subindo o Banco de Dados (Docker)
Na raiz do projeto, inicialize a infraestrutura através do Docker Compose:
```bash
docker compose -f infra/docker-compose.yml up -d
```
*Isso criará o container do PostgreSQL mapeado para a porta 5432 com o schema `chegae`.*

### 3. Executando o Backend
Abra um novo terminal na pasta `backend` e inicie a API do Spring Boot. O Flyway aplicará todas as *migrations* e o Seeder injetará os dados de demonstração automaticamente.
```bash
cd backend
mvn clean spring-boot:run
```
*A API ficará disponível em `http://localhost:8080/api`.*

### 4. Executando o Frontend
Abra um novo terminal na pasta `frontend`, instale as dependências e inicie o servidor de desenvolvimento:
```bash
cd frontend
npm install
npm run dev
```
*Acesse a aplicação no navegador via `http://localhost:5173`.*

---

## 🧪 Contas Demonstrativas (Seeder)

Para testar a plataforma e as regras de negócio em ação, utilize os e-mails pré-cadastrados (o sistema utiliza um mock seguro para login imediato na versão de desenvolvimento):

| E-mail | Perfil | Reputação |
| --- | --- | --- |
| `pedro@chegae.dev` | Embaixador | ⭐ 4.9 |
| `marcos@chegae.dev` | Embaixador | ⭐ 5.0 |
| `caio@chegae.dev` | Embaixador | ⭐ 4.9 |
| `baixa@chegae.dev` | Participante (Bloqueado) | ⭐ 1.5 |
| `tester.pendente@chegae.dev`| Participante (Com Pendência) | ⭐ 3.5 |

---

## 🤖 Executando os Testes E2E (Playwright)

A qualidade da interface é atestada por uma suite completa cobrindo 8 fluxos principais de rastreabilidade do sistema.

Para rodar os testes:
```bash
cd frontend
npx playwright test --reporter=list
```

Se desejar ver a automação operando visualmente o navegador:
```bash
npx playwright test --headed
```

---

## 📈 Testes de Carga e SLA (k6)

O ChegAê também passou por rigorosas rodadas de avaliação de performance (SLA) para garantir sua escalabilidade, suportando picos de tráfego (acessos concorrentes ao Feed e Fila de Espera).

Os scripts para os testes de carga e pico foram desenvolvidos com **k6** e ficam alocados em:
```text
tests/load/                 Scripts k6 otimizados
docs/sla/README.md          Relatório final das medições de performance
```

Para rodar os testes localmente (caso possua o k6 instalado):
```bash
cd tests/load
k6 run spike_test.js
```

---

*Projeto acadêmico desenvolvido para consolidar os conceitos de Arquitetura de Software, Engenharia de Requisitos e Qualidade de Software.*
