> Documento histórico: este README foi preservado como referência da estrutura anterior. Para os caminhos atuais do projeto, consulte `../README.md`.
# ChegaA

Projeto unificado do ChegaA: frontend em React + Vite na raiz e arquivos de banco em `pi2/`.

## Frontend

```bash
npm install
npm run dev
```

Depois abra o endereÃ§o informado pelo Vite no navegador.

### Scripts

- `npm run dev`: inicia o servidor local de desenvolvimento.
- `npm run build`: gera a versÃ£o de produÃ§Ã£o em `dist/`.
- `npm run preview`: abre uma prÃ©via local do build.

## Banco de dados

Os arquivos de banco existentes foram preservados em `pi2/`.

```bash
cd pi2
docker compose up -d
docker compose ps
```

### Acesso ao PostgreSQL

- Host: `localhost`
- Porta: `5432`
- Database: `chegae`
- UsuÃ¡rio: `chegae`
- Senha: `chegae`

### Migrations

- `pi2/src/main/resources/db/migration/V1__esquema_inicial.sql`
- `pi2/src/main/resources/db/migration/V2__carga_inicial.sql`

## O que foi representado do PDF

- Feed de eventos presenciais, chamados de rolÃªs.
- Categorias de eventos como futebol, pets, patinaÃ§Ã£o, esporte, cultura e lazer.
- Cadastro/criaÃ§Ã£o de evento com local, CEP, data, horÃ¡rio e limite de participantes.
- Regra de honra com destaque para usuÃ¡rio Embaixador.
- Limite padrÃ£o de 20 participantes e limite de 50 para Embaixador.
- InscriÃ§Ã£o com vagas confirmadas e fila de espera FIFO.
- ComentÃ¡rios liberados apenas para confirmados ou usuÃ¡rios na fila.
- DenÃºncia de evento/usuÃ¡rio e painel de moderaÃ§Ã£o com travamento de evento.
- Ãrea de perfil com honra, inscriÃ§Ãµes e avaliaÃ§Ãµes pendentes.

## PrÃ³ximos passos tÃ©cnicos

- Substituir `localStorage` por PostgreSQL/Supabase.
- Implementar autenticaÃ§Ã£o real.
- Integrar ViaCEP para endereÃ§o.
- Criar backend com tratamento de concorrÃªncia para inscriÃ§Ãµes.
- Implementar cÃ¡lculo real de honra apÃ³s eventos concluÃ­dos.

