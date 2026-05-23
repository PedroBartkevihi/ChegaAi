# ChegaAê

Projeto organizado em formato de monorepo para separar claramente front-end, back-end, banco de dados, infraestrutura, documentação, configurações e scripts auxiliares.

## Estrutura

```text
frontend/                  Aplicação React + Vite.
backend/                   Espaço reservado para futura API e regras de negócio.
database/postgres/         Migrations SQL do PostgreSQL.
database/graphdb/          Scripts SPARQL do GraphDB.
infra/                     Docker Compose e arquivos de infraestrutura local.
docs/                      Documentação técnica e material preservado do projeto.
config/                    Templates e configurações compartilhadas do projeto.
scripts/                   Scripts auxiliares de automação.
```

## Front-end

```powershell
cd .\frontend
npm install
npm run dev
```

Para gerar build de produção:

```powershell
cd .\frontend
npm run build
```

## Banco e infraestrutura local

```powershell
cd C:\ChegaÊ
docker compose -f .\infra\docker-compose.yml up -d
docker compose -f .\infra\docker-compose.yml ps
```

Migrations PostgreSQL:

- `database/postgres/migrations/V1__esquema_inicial.sql`
- `database/postgres/migrations/V2__carga_inicial.sql`

Scripts GraphDB:

- `database/graphdb/01_carga_inicial.sparql`
- `database/graphdb/02_mapeamento_labels.sparql`
- `database/graphdb/03_query.sparql`

## Documentação

- `docs/overview-original.md`: README anterior da raiz, preservado como histórico.
- `docs/database.md`: detalhes do PostgreSQL, GraphDB e migrations.

## Observações de organização

- `node_modules/`, `dist/` e logs locais continuam ignorados pelo Git.
- O repositório Git aninhado que existia em `pi2/.git` foi removido.
- A pasta legada `pi2/` foi removida somente depois que ficou vazia.
