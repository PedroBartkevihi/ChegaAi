# Deploy do ChegaAí

## Arquitetura da versão online

| Parte | Onde | Por quê |
|---|---|---|
| Banco PostgreSQL | [Neon](https://neon.com), região AWS São Paulo (`aws-sa-east-1`) | Plano gratuito e mesma cidade da API |
| API Spring Boot | [Google Cloud Run](https://cloud.google.com/run), região `southamerica-east1` (São Paulo) | Escala a zero sem custo dentro da cota gratuita e acorda em cerca de 30 s com 1 vCPU |
| Front-end Next.js | [Render](https://render.com), site estático (`render.yaml`) | Gratuito e não dorme |

O plano gratuito do Render também hospedaria a API, mas com 0,1 de CPU o Spring Boot
levou mais de 4 minutos para iniciar e vários segundos por requisição (medido com a
própria imagem em um contêiner limitado a 0,1 CPU e 512 MB). Com 1 vCPU, como no
Cloud Run, a mesma imagem inicia em cerca de 26 s.

## 1. Banco no Neon

1. Crie um projeto na região **AWS São Paulo (`aws-sa-east-1`)**. O Neon cria projetos
   novos com PostgreSQL 18; a API foi testada nele (os testes de integração usam o 15 e passam no 18).
2. Em **Connect**, desligue o connection pooling e copie a connection string **direta**
   (sem `-pooler` no host). O pooler do Neon funciona em modo de transação, que atrapalha
   as migrations do Flyway. Ela tem o formato:

   ```
   postgresql://USUARIO:SENHA@HOST/BANCO?sslmode=require&channel_binding=require
   ```

3. Separe as partes para as variáveis da API. Na URL, deixe só o `sslmode=require`: o driver
   JDBC do PostgreSQL usa outro nome para o `channel_binding`.

   | Variável | Valor |
   |---|---|
   | `CHEGAAI_DB_URL` | `jdbc:postgresql://HOST/BANCO?sslmode=require` |
   | `CHEGAAI_DB_USER` | `USUARIO` |
   | `CHEGAAI_DB_PASSWORD` | `SENHA` |

As tabelas e os dados de demonstração são criados pela própria API na primeira inicialização (Flyway + seed).

## 2. API no Google Cloud Run

Pré-requisitos: uma conta no Google Cloud com um projeto e uma conta de faturamento
(o Google pede cartão de crédito no cadastro). Crie logo um **orçamento** em
Faturamento → Orçamentos e alertas, com um valor baixo (por exemplo R$ 10) e alertas em
50%, 90% e 100%, para ser avisado por e-mail se algo sair da cota gratuita. O orçamento só
avisa, não bloqueia gastos; quem limita o custo é o máximo de 1 instância, abaixo.

No console do Cloud Run, clique em **Criar serviço** e escolha **implantação contínua com um repositório**:

1. **Origem:** **Developer Connect**. Conecte o GitHub e instale o app do Google **só neste
   repositório** (*Only select repositories*); ele lê o código e grava o status dos builds nos commits.
2. **Build:** branch `main`, tipo **Dockerfile**, com o local `/backend/Dockerfile`. A pasta `backend` vira o contexto do build.
3. **Serviço:** nome `chegaai-api` (não muda depois e vira parte da URL), região
   `southamerica-east1`, **permitir acesso público** (quem protege as rotas é o JWT da própria API).
4. **Faturamento e escala:** **baseado em solicitações** (CPU só durante as requisições),
   escalonamento automático com mínimo de **0** instâncias e máximo de **1** (com uma só
   instância, o custo fica limitado e o seed da demonstração não roda em duas ao mesmo tempo).
5. **Contêiner:** porta `8080`, memória `512 MiB`, `1` CPU e **Otimização da CPU de inicialização** ligada.
6. **Variáveis de ambiente** (a tela aceita colar um `.env` inteiro no campo do nome):

   | Variável | Valor |
   |---|---|
   | `CHEGAAI_DB_URL`, `CHEGAAI_DB_USER`, `CHEGAAI_DB_PASSWORD` | Do passo 1 |
   | `CHEGAAI_JWT_SECRET` | Valor aleatório com pelo menos 32 caracteres (comando abaixo) |
   | `CHEGAAI_CORS_ORIGENS` | URL do front-end no Render (preencha depois do passo 3) |
   | `CHEGAAI_DEMO_RECRIAR` | `true` |
   | `CHEGAAI_DB_POOL_MAX` | `5` |
   | `CHEGAAI_DB_POOL_MIN` | `1` |
   | `LOGGING_LEVEL_CHEGAAI` | `INFO` |

   Para gerar o segredo do JWT:

   ```bash
   node -e "console.log(require('node:crypto').randomBytes(48).toString('base64url'))"
   ```

   Não deixe o `CHEGAAI_JWT_SECRET` de fora: sem ele, a API gera uma chave aleatória a
   cada inicialização e todo mundo é deslogado sempre que a instância reinicia (no Cloud Run,
   a cada vez que ela acorda). A API se recusa a subir com
   `chave-de-desenvolvimento-local-nao-usar-em-producao`, que já foi o padrão do repositório
   e é pública: com ela, qualquer pessoa assinaria tokens válidos.

   Senha do banco e segredo do JWT também podem ir para o Secret Manager e ser
   referenciados no serviço, em vez de ficarem como variáveis de texto.

### Permissões da conta de serviço (projetos novos)

Em projetos novos, o Google não dá mais papéis automáticos à conta de serviço padrão
(`NUMERO-DO-PROJETO-compute@developer.gserviceaccount.com`), que é a que o build usa. Sem
eles, o primeiro build falha em poucos segundos, sem rodar nenhuma etapa e sem log ("não tem
permissão para gravar os registros"), e o serviço fica mostrando a página provisória do Cloud Run.

Em **IAM e administrador → IAM**, edite essa conta e adicione os papéis:

| Papel | Para quê |
|---|---|
| Conta de serviço do Cloud Build | gerar a imagem, gravar o log e guardar a imagem no Artifact Registry |
| Administrador do Cloud Run | publicar a nova revisão do serviço |
| Usuário da conta de serviço | implantar um serviço que roda com essa conta |
| Acessador de token de leitura do Developer Connect | ler o código do GitHub pela conexão |

Depois, em **Cloud Build → Histórico**, abra o build com falha e clique em **Tentar novamente**
(os papéis podem levar um ou dois minutos para valer).

### Conferir e limpar

Depois do primeiro deploy, confira:

- `https://SEU-SERVICO.run.app/api/actuator/health` responde `{"status":"UP"}`;
- `https://SEU-SERVICO.run.app/api/swagger-ui.html` mostra a documentação da API;
- `https://SEU-SERVICO.run.app/api/eventos` traz os rolés de demonstração (as migrations e o seed rodaram).

Cada push na `main` gera uma nova imagem e uma nova revisão. As imagens ficam no
Artifact Registry (repositório `cloud-run-source-deploy`), e cada uma tem cerca de 400 MB,
contra 0,5 GB gratuitos. Em **Artifact Registry → Repositórios → `cloud-run-source-deploy`
→ Editar → Políticas de limpeza**, marque **Excluir artefatos** (e não a simulação) e crie:

| Nome | Tipo | Configuração |
|---|---|---|
| `manter-2-recentes` | Manter as versões mais recentes | 2 versões |
| `excluir-antigas` | Exclusão condicional | qualquer estado de tag, mais antigo que 1 dia |

A política de manter vale mais que a de excluir: ficam sempre as 2 imagens mais recentes
(a atual e uma para voltar, se preciso), e o resto sai em cerca de um dia.

## 3. Front-end no Render

1. No Render, crie um **Blueprint** (*New → Blueprint*) a partir deste repositório, branch
   `main`; ele lê o `render.yaml`. Conecte o repositório pelo app do Render no GitHub, e não
   pela URL pública: só assim o site é publicado sozinho a cada merge. Se o repositório não
   aparecer na lista, libere-o em `github.com/settings/installations` → Render → *Configure*
   → *Repository access*.
2. Informe `NEXT_PUBLIC_API_BASE_URL` com a URL da API terminando em `/api`,
   por exemplo `https://chegaai-api-xxxxx.southamerica-east1.run.app/api`.
3. Quando o site estiver no ar (a URL fica no topo da página do serviço, por exemplo
   `https://chegaai-web.onrender.com`), volte ao Cloud Run, clique em **Editar e implantar nova
   revisão**, coloque essa URL, sem barra no fim, em `CHEGAAI_CORS_ORIGENS` e implante. Isso não
   refaz o build. Até lá, o site abre, mas não consegue entrar nem carregar os rolés.

O endereço da API é embutido no build do front-end: se ele mudar, refaça o deploy do site no Render.

## Limites e comportamento da versão online

- **API em repouso:** sem acessos por alguns minutos, o Cloud Run desliga a instância. A próxima visita leva cerca de 30 s. O front-end chama o health check assim que a página abre, para adiantar esse tempo, e mostra um aviso se o login demorar.
- **Demonstração reiniciada:** com `CHEGAAI_DEMO_RECRIAR=true`, cada inicialização renova os cenários fictícios com datas atuais e preserva a identidade das contas demo. O histórico concluído que envolve visitantes é mantido, inclusive os descontos originais por ausência. As contas demo voltam a ativas e seus cancelamentos tardios fictícios são removidos. Contas reais não são resetadas; retirar uma inscrição demo de um rolé futuro promove a fila normalmente.
- **GraphDB:** não é usado na versão online. `GET /api/recomendacoes/status` responde `available: false` e a busca semântica fica indisponível; o resto da aplicação não depende dele.

## Produção local com Docker

```bash
docker compose up --build
```

- Front-end: `http://localhost:3000`
- API: `http://localhost:8080/api` (Swagger em `/api/swagger-ui.html`)
- Banco: PostgreSQL no volume `chegaai_pgdata`

Sem `CHEGAAI_JWT_SECRET`, a API gera uma chave JWT aleatória a cada inicialização: funciona,
mas os logins caem sempre que o contêiner da API reinicia. Para mantê-los, defina a variável
no shell ou num arquivo `.env` ao lado do `docker-compose.yml` (ignorado pelo Git), que o
Compose repassa para a API:

```bash
echo "CHEGAAI_JWT_SECRET=$(node -e "console.log(require('node:crypto').randomBytes(48).toString('base64url'))")" >> .env
docker compose up --build
```

Para expor a aplicação fora da sua máquina, defina o segredo dessa forma e troque a senha do banco.
