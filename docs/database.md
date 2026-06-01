# Banco de dados - ChegAê

## Integrantes do grupo

- Caio Fernandes Ferreira Soares Contursi
- Marcos Lacaille Caldas Romano
- Pedro Farias Bartkevihi de Mello e Alvim

## Detalhes do Banco de Dados (SGBDR)

- **SGBDR Utilizado:** PostgreSQL
- **Versão:** 15
- **Imagem Docker:** `postgres:15-alpine`
- **Banco:** `chegae`
- **Usuário:** `chegae`
- **Porta:** `5432`
- **Extensão:** `pgcrypto` (necessária para `gen_random_uuid()`)
- **Execução local:** subir o PostgreSQL com `docker compose -f .\infra\docker-compose.yml up -d`.
- **Esquema Físico (DDL):** o script `database/postgres/migrations/V1__esquema_inicial.sql` contém tabelas, colunas, tipos, chaves primárias simples e compostas, chaves estrangeiras, índices, restrições de integridade e restrições de domínio.
- **Carga Inicial (DML):** o script `database/postgres/migrations/V2__carga_inicial.sql` contém `INSERT` e `UPDATE` respeitando a integridade referencial.

## Ordem de execução dos scripts

1. `database/postgres/migrations/V1__esquema_inicial.sql` (DDL)
2. `database/postgres/migrations/V2__carga_inicial.sql` (DML)

## Subir ambiente local

```powershell
cd C:\ChegaÊ
docker compose -f .\infra\docker-compose.yml up -d
docker compose -f .\infra\docker-compose.yml ps
```

## Acesso ao banco

- Host: `localhost`
- Porta: `5432`
- Database: `chegae`
- Usuário: `chegae`
- Senha: `chegae`

## Flyway (migrations)

- Diretório: [../database/postgres/migrations/](../database/postgres/migrations/)
- Esquema (V1): [../database/postgres/migrations/V1__esquema_inicial.sql](../database/postgres/migrations/V1__esquema_inicial.sql)
- Carga inicial (V2): [../database/postgres/migrations/V2__carga_inicial.sql](../database/postgres/migrations/V2__carga_inicial.sql)
- O backend usa Flyway por padrão. Rodando a API a partir de `backend/`, o caminho configurado é `filesystem:../database/postgres/migrations`.

## GraphDB

Scripts SPARQL:

- [../database/graphdb/01_carga_inicial.sparql](../database/graphdb/01_carga_inicial.sparql)
- [../database/graphdb/02_mapeamento_labels.sparql](../database/graphdb/02_mapeamento_labels.sparql)
- [../database/graphdb/03_query.sparql](../database/graphdb/03_query.sparql)

O Docker Compose sobe o GraphDB, mas a criação do repositório `chegae` e a importação dos scripts SPARQL ainda devem ser feitas no Workbench do GraphDB, salvo se uma automação de provisionamento for adicionada depois.

## Regras de negócio na aplicação (fora do banco)

- O organizador do evento deve ser `EMBAIXADOR` ou `ADMINISTRADOR`.
- O aplicador de penalidade deve ser `ADMINISTRADOR`.
- Apenas usuários `PARTICIPANTE` podem avaliar eventos.

Os scripts V1 (DDL) e V2 (DML) atendem aos requisitos da disciplina.
