# ChegAê

Plataforma para conectar pessoas em atividades presenciais, com foco em reputação, segurança, controle de vagas e lista de espera.

## Estrutura

```text
frontend/                  Aplicação React + Vite
backend/                   API REST em Spring Boot
database/
  postgres/migrations/     Migrations SQL versionadas
  graphdb/                 Scripts SPARQL para GraphDB
infra/                     Docker Compose local
docs/                      Documentação técnica e materiais auxiliares
```

## Requisitos

- Node.js 20.19 ou superior
- npm 10 ou superior
- JDK 17 ou superior
- Maven 3.9 ou superior
- Docker Desktop ou Docker Engine com Docker Compose
- Opcional: GraphDB Workbench em `http://localhost:7200`

No Windows, se o PowerShell bloquear `npm`, use `npm.cmd` nos comandos.

## Subir a infraestrutura

```powershell
docker compose -f .\infra\docker-compose.yml up -d
docker compose -f .\infra\docker-compose.yml ps
```

Serviços locais:

- PostgreSQL: `localhost:5432`, banco `chegae`, usuário `chegae`, senha `chegae`
- GraphDB: `http://localhost:7200`

## Rodar o backend

```powershell
cd .\backend
mvn spring-boot:run
```

A API roda em `http://localhost:8080/api`.

O backend usa Flyway por padrão com as migrations em `database/postgres/migrations`.
Se você rodar o backend a partir de outro diretório, informe o caminho das migrations:

```powershell
$env:CHEGAE_FLYWAY_LOCATIONS = "filesystem:C:/ChegaÊ/database/postgres/migrations"
mvn -f C:\ChegaÊ\backend\pom.xml spring-boot:run
```

Variáveis úteis:

- `CHEGAE_DB_URL`
- `CHEGAE_DB_USER`
- `CHEGAE_DB_PASSWORD`
- `CHEGAE_FLYWAY_ENABLED`
- `CHEGAE_FLYWAY_LOCATIONS`
- `CHEGAE_GRAPHDB_URL`
- `CHEGAE_GRAPHDB_REPOSITORY`
- `CHEGAE_VIACEP_URL`
- `CHEGAE_SHOW_SQL`

## Rodar o frontend

```powershell
cd .\frontend
npm install
npm run dev
```

A aplicação roda em `http://localhost:5173`.

Por padrão, o Vite envia chamadas `/api` para `http://localhost:8080`. Para usar outro backend:

```powershell
$env:VITE_API_BASE_URL = "http://localhost:8080/api"
npm run dev
```

## Contas demonstrativas

| E-mail | Perfil | Reputação |
| --- | --- | --- |
| `pedro@chegae.dev` | Embaixador | 4.9 |
| `caio@chegae.dev` | Embaixador | 4.9 |
| `marcos@chegae.dev` | Embaixador | 5.0 |
| `baixa@chegae.dev` | Participante com reputação baixa | 2.5 |
| `moderador@chegae.dev` | Administrador | 5.0 |

## Validação rápida

```powershell
cd .\frontend
npm run build

cd ..\backend
mvn test
```

Se você já tinha um volume PostgreSQL antigo criado antes das migrations, pode ser necessário recriar o banco local para evitar divergência de schema:

```powershell
cd C:\ChegaÊ
docker compose -f .\infra\docker-compose.yml down -v
docker compose -f .\infra\docker-compose.yml up -d
```
