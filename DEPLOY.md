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

1. Crie um projeto na região **AWS São Paulo (`aws-sa-east-1`)**.
2. Copie a connection string **direta** (sem o pooler, isto é, sem `-pooler` no host).
   O pooler do Neon funciona em modo de transação, que atrapalha as migrations do Flyway.
   Ela tem o formato:

   ```
   postgresql://USUARIO:SENHA@HOST/BANCO?sslmode=require
   ```

3. Separe as partes para as variáveis da API:

   | Variável | Valor |
   |---|---|
   | `CHEGAAI_DB_URL` | `jdbc:postgresql://HOST/BANCO?sslmode=require` |
   | `CHEGAAI_DB_USER` | `USUARIO` |
   | `CHEGAAI_DB_PASSWORD` | `SENHA` |

As tabelas e os dados de demonstração são criados pela própria API na primeira inicialização (Flyway + seed).

## 2. API no Google Cloud Run

Pré-requisitos: uma conta no Google Cloud com um projeto e uma conta de faturamento
(o Google pede cartão de crédito no cadastro). Crie logo um **alerta de orçamento**
(Faturamento → Orçamentos e alertas) com um valor baixo, para ser avisado se algo sair da cota gratuita.

No console do Cloud Run, crie um serviço com **implantação contínua a partir de um repositório**:

1. **Origem:** GitHub, este repositório, branch `^main$`. O Cloud Build pede para instalar o app dele no GitHub.
2. **Tipo de build:** Dockerfile, com o local `/backend/Dockerfile`. A pasta `backend` vira o contexto do build.
3. **Serviço:** nome `chegaai-api`, região `southamerica-east1`, acesso **público** (sem autenticação; quem protege as rotas é o JWT da própria API).
4. **Faturamento e escala:** faturamento por solicitação (CPU só durante as requisições), mínimo de **0** instâncias e máximo de **1**.
5. **Contêiner:** porta `8080`, memória `512 MiB`, `1` CPU e **aumento de CPU na inicialização** ligado.
6. **Variáveis de ambiente:**

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

   Senha do banco e segredo do JWT também podem ir para o Secret Manager e ser
   referenciados no serviço, em vez de ficarem como variáveis de texto.

Depois do primeiro deploy, confira:

- `https://SEU-SERVICO.run.app/api/actuator/health` responde `{"status":"UP"}`;
- `https://SEU-SERVICO.run.app/api/swagger-ui.html` mostra a documentação da API.

Cada push na `main` gera uma nova imagem e uma nova revisão. As imagens ficam no
Artifact Registry (repositório `cloud-run-source-deploy`); configure lá uma
**política de limpeza** que mantenha só as últimas versões, porque cada imagem tem
cerca de 400 MB e o armazenamento gratuito é pequeno.

## 3. Front-end no Render

1. No Render, crie um **Blueprint** a partir deste repositório (branch `main`). Ele lê o `render.yaml`.
2. Informe `NEXT_PUBLIC_API_BASE_URL` com a URL da API terminando em `/api`,
   por exemplo `https://chegaai-api-xxxxx.southamerica-east1.run.app/api`.
3. Quando o site estiver no ar (por exemplo `https://chegaai-web.onrender.com`),
   volte ao Cloud Run, coloque essa URL em `CHEGAAI_CORS_ORIGENS` e implante uma nova revisão.

O endereço da API é embutido no build do front-end: se ele mudar, refaça o deploy do site no Render.

## Limites e comportamento da versão online

- **API em repouso:** sem acessos por alguns minutos, o Cloud Run desliga a instância. A próxima visita leva cerca de 30 s. O front-end chama o health check assim que a página abre, para adiantar esse tempo, e mostra um aviso se o login demorar.
- **Demonstração recriada:** com `CHEGAAI_DEMO_RECRIAR=true`, cada inicialização apaga as contas de demonstração (e o que é delas) e semeia tudo de novo, com datas atuais. Contas e eventos criados por visitantes não são tocados.
- **GraphDB:** não é usado na versão online. `GET /api/recomendacoes/status` responde `available: false` e a busca semântica fica indisponível; o resto da aplicação não depende dele.

## Produção local com Docker

```bash
docker compose up --build
```

- Front-end: `http://localhost:3000`
- API: `http://localhost:8080/api` (Swagger em `/api/swagger-ui.html`)
- Banco: PostgreSQL no volume `chegaai_pgdata`

O Compose usa o segredo JWT padrão de desenvolvimento. Para expor a aplicação fora
da sua máquina, defina `CHEGAAI_JWT_SECRET` e troque a senha do banco.
