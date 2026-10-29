# Testes ponta a ponta

Requisitos: Docker, Java 17, Node.js 20.9+ e dependências do front instaladas com `npm ci`.
Os exemplos abaixo usam PowerShell, a partir da raiz do repositório, com terminais separados.

As fixtures aceitam apenas front, API e PostgreSQL em loopback. O banco deve se chamar
**`chegaai_e2e`**, sem parâmetros adicionais na URL. Cada tentativa cria identidades e dados
próprios; não são usadas contas demo nem dados da versão online. O navegador também bloqueia
origens diferentes das duas configuradas, inclusive uma API embutida por engano em um build antigo.

## 1. Banco descartável

```powershell
docker run --rm --name chegaai-e2e-local -e POSTGRES_DB=chegaai_e2e -e POSTGRES_USER=chegaai_e2e -e POSTGRES_PASSWORD=chegaai_e2e -p 127.0.0.1:55436:5432 postgres:15-alpine
```

As credenciais acima são fictícias e exclusivas deste banco local. Não use o banco de desenvolvimento.

## 2. API local

Em outro terminal:

```powershell
cd backend
$env:PORT = '8086'
$env:CHEGAAI_DB_URL = 'jdbc:postgresql://127.0.0.1:55436/chegaai_e2e'
$env:CHEGAAI_DB_USER = 'chegaai_e2e'
$env:CHEGAAI_DB_PASSWORD = 'chegaai_e2e'
$env:CHEGAAI_CORS_ORIGENS = 'http://127.0.0.1:3006'
$env:CHEGAAI_DEMO_RECRIAR = 'false'
$env:CHEGAAI_LIMITE_LOGIN_IP = '1000000'
$env:CHEGAAI_LIMITE_LOGIN_PAR = '1000000'
$env:CHEGAAI_LIMITE_CADASTRO_IP = '1000000'
$env:CHEGAAI_LIMITE_CRIAR_EVENTO = '1000000'
$env:CHEGAAI_LIMITE_CRIAR_EVENTO_DIA = '1000000'
$env:CHEGAAI_LIMITE_COMENTAR = '1000000'
$env:CHEGAAI_LIMITE_COMENTAR_HORA = '1000000'
./mvnw.cmd -B -ntp -DskipTests package
java -jar target/chegaai-backend-1.0.0-SNAPSHOT.jar
```

A suíte inteira cria centenas de dados pelo mesmo IP de loopback: as cotas acima são
maiores, mas o limitador continua ativo. Os IT de segurança usam cotas baixas, relógio
controlado e servidor Tomcat real para verificar 429, spoofing, expiração e concorrência.
Não aplique esses valores de teste na instalação pública.

Espere `http://127.0.0.1:8086/api/actuator/health` responder `{"status":"UP"}`.
Em Linux/macOS, use `./mvnw` e a sintaxe `export NOME=valor` para definir as mesmas variáveis.

## 3. Site estático e suíte completa

Em um terceiro terminal:

```powershell
cd frontend
npm ci
npm run test:session
npx playwright install chromium
$env:E2E_DATABASE_URL = 'postgresql://chegaai_e2e:chegaai_e2e@127.0.0.1:55436/chegaai_e2e'
$env:E2E_API_BASE_URL = 'http://127.0.0.1:8086/api'
$env:E2E_BASE_URL = 'http://127.0.0.1:3006'
$env:E2E_MODE = 'static'
$env:NEXT_OUTPUT = 'export'
$env:NEXT_PUBLIC_API_BASE_URL = $env:E2E_API_BASE_URL
npm run build
npx playwright test --repeat-each=2 --retries=0
```

O Playwright inicia e encerra o servidor de arquivos em `tests/serve-static.mjs`; não é preciso
abrir outro servidor do front. O endereço da API é embutido durante o build: ao mudar essa URL,
refaça o export. Front e API precisam ter origens diferentes no modo estático.

Os três testes de sessão usam o runner do Node. O Playwright descobre somente `*.spec.ts`,
com fluxos reais de presença/avaliação, homônimos por UUID, atualização de honra e respostas
atrasadas entre contas.

O CI usa esse mesmo desenho: export completo, API separada, CORS e duas execuções de cada
cenário sem retries, no mesmo banco e sem reset entre elas. Relatório local em
`playwright-report/`; falhas no CI disponibilizam o relatório e o log da API como artefato.

## Modo de desenvolvimento

No terminal do front, mantendo as três variáveis `E2E_*_URL` anteriores:

```powershell
$env:E2E_MODE = 'dev'
Remove-Item Env:NEXT_OUTPUT, Env:NEXT_PUBLIC_API_BASE_URL -ErrorAction SilentlyContinue
npx playwright test --retries=0
```

O Playwright inicia `next dev` com proxy para a API configurada. Se já houver um servidor nessa
porta, ele é reutilizado no modo dev local; confira se pertence a este checkout e usa a API correta.

## Dados e relógio

Cada tentativa, retry ou repetição usa um UUID novo nos e-mails, títulos e comentários.
As verificações conferem resposta da API, estado persistido e comportamento na tela, sem
depender de qual card ficou em primeiro lugar ou dos cenários de demonstração.

Para testar presença, conclusão e avaliação, a fixture cria um rolé futuro pela API e altera
somente sua data para duas horas atrás por SQL. A atualização é limitada ao ID do rolé,
organizador e título registrados pela própria fixture e precisa atingir exatamente uma linha.
Não há endpoint de teste, alteração de conta ou atalho de relógio na aplicação.

Rolés próprios ainda futuros são removidos ao terminar cada cenário; históricos iniciados ou
concluídos e contas de teste ficam no banco descartável. A suíte pode ser repetida sem reset.
Ao terminar todas as execuções, encerre a API com Ctrl+C e remova o banco parando o contêiner:

```powershell
docker stop chegaai-e2e-local
```

Esse comando atua apenas no contêiner criado no primeiro passo, que usa `--rm` e não tem volume persistente.
