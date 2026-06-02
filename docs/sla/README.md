# Medições de SLA - Testes de Carga

Este documento registra os testes de carga do backend do ChegAe usando k6. A suite cobre dois serviços internos, incluindo uma operação de escrita no banco de dados.

---

## Ferramenta utilizada

Os testes foram implementados com [k6](https://k6.io/), ferramenta de testes de carga open-source baseada em JavaScript, projetada para cenários de alta concorrência com baixo consumo de recursos no agente de carga. Neste projeto, o k6 foi instalado de forma portátil em `.tools/k6/k6.exe`, pasta ignorada pelo Git, de modo a não poluir o repositório com binários.

Os scripts também geram relatórios HTML e JSON em `docs/sla/reports/` por meio de `handleSummary`. Quando disponível, também é possível habilitar o Web Dashboard do k6 por variáveis de ambiente para acompanhamento em tempo real durante a execução.

---

## Ambiente de execução

### Hardware e sistema operacional

| Componente | Detalhe |
| --- | --- |
| Sistema operacional | Microsoft Windows 11 Pro 10.0.22631 |
| Shell | PowerShell |
| Processador | AMD Ryzen 5 2400G (4 núcleos / 8 threads, 3,6 GHz base) |
| Memória RAM | 15,9 GB |
| Armazenamento | SSD (local) |

> **Nota sobre o ambiente local:** por se tratar de hardware de desenvolvimento compartilhado (CPU e memória divididos entre o backend, o banco de dados, o k6 e o sistema operacional), os números de latência e vazão obtidos não representam capacidade de produção. Os valores devem ser interpretados como linha de base relativa, não como SLA absoluto.

### Stack da aplicação

| Componente | Versão / Detalhe |
| --- | --- |
| Backend | Spring Boot 3.3.0, Java 17 |
| Banco de dados | PostgreSQL 15 via Docker Compose |
| Serviços auxiliares | GraphDB 10.6 via Docker Compose |
| URL base da API | `http://localhost:8080/api` |
| Branch testada | `feat/medicao-teste-carga` |

### Configuração do pool de conexões (Spring Boot / HikariCP)

O Spring Boot 3.x utiliza HikariCP como pool padrão. Nenhuma customização explícita foi registrada para esta medição, portanto os valores aplicados foram os defaults do HikariCP:

| Parâmetro | Valor padrão |
| --- | --- |
| `maximumPoolSize` | 10 |
| `minimumIdle` | 10 |
| `connectionTimeout` | 30 000 ms |
| `idleTimeout` | 600 000 ms |
| `maxLifetime` | 1 800 000 ms |

> **Impacto observado:** com 30 VUs no teste de listagem e pool máximo de 10 conexões, parte das requisições aguarda conexão disponível. Isso contribui para a dispersão de latência vista no p95 (83,05 ms) em relação à média (62,81 ms). Aumentar o `maximumPoolSize` para 20–30 é uma das otimizações recomendadas na seção correspondente.

### Configuração do Docker Compose

O PostgreSQL 15 foi executado com configurações padrão de imagem oficial, sem ajuste de `shared_buffers`, `work_mem` ou `max_connections`. O GraphDB 10.6 foi mantido ativo durante os testes, competindo por memória RAM com o backend.

---

## Como executar

### Pré-requisitos

Suba a infraestrutura e o backend antes dos testes:

```powershell
cd C:\ChegaAe
docker compose -f .\infra\docker-compose.yml up -d

cd C:\ChegaAe\backend
mvn spring-boot:run
```

### Execução padrão

Em outro terminal, execute os testes a partir da raiz do repositório:

```powershell
cd C:\ChegaAe
$env:BASE_URL = "http://localhost:8080/api"
.\.tools\k6\k6.exe run .\tests\load\eventos-feed.js
.\.tools\k6\k6.exe run .\tests\load\comentarios-write.js
```

### Coleta de série temporal (para gráficos estáticos)

```powershell
$env:BASE_URL = "http://localhost:8080/api"
.\.tools\k6\k6.exe run --out json=docs/sla/reports/eventos-feed-timeseries.jsonl .\tests\load\eventos-feed.js
.\.tools\k6\k6.exe run --out json=docs/sla/reports/comentarios-write-timeseries.jsonl .\tests\load\comentarios-write.js
```

### Geração dos gráficos SVG

```powershell
node .\tests\load\tools\generate-k6-chart.mjs .\docs\sla\reports\eventos-feed-timeseries.jsonl .\docs\sla\reports\eventos-feed-latency.svg "Duracao media das requisicoes - GET /api/eventos"
node .\tests\load\tools\generate-k6-chart.mjs .\docs\sla\reports\comentarios-write-timeseries.jsonl .\docs\sla\reports\comentarios-write-latency.svg "Duracao media das requisicoes - POST comentarios"
```

### Geração da tabela de métricas por intervalo

```powershell
node .\tests\load\tools\generate-interval-metrics.mjs
```

### Execução com k6 no PATH

Se `k6.exe` estiver no PATH, os comandos equivalentes no PowerShell são:

```powershell
$env:BASE_URL = "http://localhost:8080/api"
k6 run tests/load/eventos-feed.js
k6 run tests/load/comentarios-write.js
```

Em shells Unix, a variável também pode ser passada inline:

```bash
BASE_URL=http://localhost:8080/api k6 run tests/load/eventos-feed.js
BASE_URL=http://localhost:8080/api k6 run tests/load/comentarios-write.js
```

### Execução curta de validação (smoke test)

```powershell
$env:LOAD_PROFILE = "smoke"
.\.tools\k6\k6.exe run .\tests\load\eventos-feed.js
.\.tools\k6\k6.exe run .\tests\load\comentarios-write.js
Remove-Item Env:\LOAD_PROFILE
```

O perfil `smoke` é útil para verificar rapidamente conectividade e saúde básica dos endpoints antes de iniciar uma bateria de carga completa. Recomenda-se sempre rodar o smoke test após reiniciar o backend ou recriar os containers.

### Web Dashboard do k6 (quando suportado)

```powershell
$env:K6_WEB_DASHBOARD = "true"
$env:K6_WEB_DASHBOARD_EXPORT = "docs/sla/reports/eventos-feed-dashboard.html"
.\.tools\k6\k6.exe run .\tests\load\eventos-feed.js

$env:K6_WEB_DASHBOARD_EXPORT = "docs/sla/reports/comentarios-write-dashboard.html"
.\.tools\k6\k6.exe run .\tests\load\comentarios-write.js
```

---

## Serviços testados

| Serviço | Tipo de operação | Endpoint | Script k6 | Relatório HTML |
| --- | --- | --- | --- | --- |
| Listagem de eventos | Leitura | `GET /api/eventos` | `tests/load/eventos-feed.js` | `docs/sla/reports/eventos-feed-dashboard.html` |
| Publicação de comentários | Inserção | `POST /api/eventos/{id}/comentarios` | `tests/load/comentarios-write.js` | `docs/sla/reports/comentarios-write-dashboard.html` |

---

## Gráficos de evolução temporal

Os gráficos abaixo usam a saída JSONL do k6 para plotar a duração média das requisições por segundo, as linhas globais de p95/p99 e a concorrência em usuários virtuais.

![Gráfico de latência da listagem de eventos](reports/eventos-feed-latency.png)

![Gráfico de latência da publicação de comentários](reports/comentarios-write-latency.png)

---

## Métricas por intervalo

A exigência de observar os resultados em intervalos de 1s, 5s, 10s, 30s e 1min foi atendida a partir das séries temporais JSONL geradas pelo k6. O arquivo completo está em `docs/sla/reports/interval-metrics.md`.

### Resumo — Listagem de eventos

| Granularidade | Latência média das janelas | p95 médio das janelas | Vazão média | Concorrência máxima | Taxa de erro média |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1s | 67,45 ms | 82,97 ms | 19,63 req/s | 30 | 0,00% |
| 5s | 66,87 ms | 111,45 ms | 19,63 req/s | 30 | 0,00% |
| 10s | 66,09 ms | 102,07 ms | 19,63 req/s | 30 | 0,00% |
| 30s | 60,83 ms | 81,12 ms | 15,27 req/s | 30 | 0,00% |
| 1min | 59,73 ms | 78,36 ms | 11,45 req/s | 30 | 0,00% |

### Resumo — Publicação de comentários

| Granularidade | Latência média das janelas | p95 médio das janelas | Vazão média | Concorrência máxima | Taxa de erro média |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1s | 35,38 ms | 39,36 ms | 6,03 req/s | 15 | 0,00% |
| 5s | 35,29 ms | 49,44 ms | 5,86 req/s | 15 | 0,00% |
| 10s | 35,26 ms | 57,23 ms | 5,86 req/s | 15 | 0,00% |
| 30s | 34,88 ms | 46,12 ms | 4,56 req/s | 15 | 0,00% |
| 1min | 35,22 ms | 43,76 ms | 3,42 req/s | 15 | 0,00% |

---

## Serviço 1 — Listagem de eventos

- **Nome do serviço:** Listagem de eventos.
- **Tipo de operação:** leitura.
- **Endpoint utilizado:** `GET /api/eventos`.
- **Arquivos envolvidos:**
  - `backend/src/main/java/chegae/controller/EventoController.java`
  - `backend/src/main/java/chegae/service/EventoService.java`
  - `backend/src/main/java/chegae/repository/EventoRepository.java`
  - `backend/src/main/java/chegae/repository/InscricaoRepository.java`
  - `backend/src/main/java/chegae/repository/ComentarioRepository.java`
- **Arquivo de medição:** `tests/load/eventos-feed.js`.
- **Data da medição:** 01/06/2026.
- **Perfil de carga padrão:**
  - carga leve: 15s até 5 usuários virtuais;
  - carga média: 20s até 15 usuários virtuais;
  - carga alta: 20s até 30 usuários virtuais;
  - redução gradual: 15s até 0 usuários virtuais.

### Resultados

| Métrica | Valor |
| --- | ---: |
| Latência média | 62,81 ms |
| p90 | 73,79 ms |
| p95 | 83,05 ms |
| Vazão média | 19,35 req/s |
| Total de requisições HTTP | 1375 |
| Usuários virtuais máximos | 30 |
| Taxa de erro | 0,00% |

### Análise dos resultados

**Estabilidade geral.** A aplicação sustentou a rampa até 30 VUs sem nenhum erro HTTP, o que indica que o caminho crítico do endpoint — consulta ao banco, montagem de DTO e serialização JSON — é robusto o suficiente para o volume testado no ambiente local.

**Dispersão de latência.** A diferença entre a média (62,81 ms) e o p95 (83,05 ms) representa um fator de ~1,32×. Essa dispersão é moderada e típica de ambientes compartilhados, onde GC do JVM, escalonamento de CPU e contenção de conexões de banco introduzem picos esporádicos. O pico isolado visível no gráfico temporal durante a subida de carga (fase de 15 → 30 VUs) é consistente com o aquecimento do pool de conexões HikariCP e o warm-up do cache de plano do PostgreSQL.

**Vazão vs. concorrência.** A vazão de ~19,35 req/s com 30 VUs implica que cada VU completa em média uma requisição a cada ~1,55 s. Dado que a latência média é de ~63 ms, o tempo restante (~1,49 s) reflete o think time configurado no script. Isso indica que o gargalo atual não é o backend em si, mas sim o perfil de carga escolhido — o serviço provavelmente suportaria vazão maior com think time reduzido ou número maior de VUs.

**Comportamento nas janelas de intervalo.** A latência média cai ligeiramente nas granularidades maiores (67 ms em 1s → 59 ms em 1min), sugerindo que os picos de aquecimento diluem-se ao longo do tempo. O p95 na janela de 5s (111,45 ms) é o valor mais alto de todas as granularidades, capturando o pico de transição de carga que a janela de 1min suaviza.

**Ausência de degradação progressiva.** Não foi observado aumento contínuo de latência ao longo do teste, o que indica ausência de vazamento de memória ou acúmulo de conexões pendentes no intervalo testado (70s totais de execução).

### Hipóteses de gargalos

- A listagem retorna todos os eventos sem paginação, fazendo o payload crescer linearmente com o número de eventos no banco.
- A montagem do DTO consulta inscrições e comentários por evento individualmente, o que pode gerar o problema N+1 de queries conforme o volume cresce.
- O payload do endpoint aumenta com a quantidade de comentários retornados por evento.
- A ordenação por `criadoEm DESC` depende do volume da tabela `evento` e da presença de índice adequado nessa coluna.
- Com 30 VUs e pool HikariCP padrão de 10 conexões, há contenção de conexões nas fases de pico.

---

## Serviço 2 — Publicação de comentários

- **Nome do serviço:** Publicação de comentários.
- **Tipo de operação:** inserção.
- **Endpoint utilizado:** `POST /api/eventos/{id}/comentarios`.
- **Arquivos envolvidos:**
  - `backend/src/main/java/chegae/controller/EventoController.java`
  - `backend/src/main/java/chegae/service/ComentarioService.java`
  - `backend/src/main/java/chegae/service/EventoService.java`
  - `backend/src/main/java/chegae/repository/ComentarioRepository.java`
  - `backend/src/main/java/chegae/repository/InscricaoRepository.java`
  - `backend/src/main/java/chegae/repository/EventoRepository.java`
  - `backend/src/main/java/chegae/repository/UsuarioRepository.java`
- **Arquivo de medição:** `tests/load/comentarios-write.js`.
- **Data da medição:** 01/06/2026.
- **Dados gravados:** comentários com prefixo `k6-test`.
- **Limpeza após medição local:** 410 comentários `k6-test` removidos do PostgreSQL depois da geração dos relatórios finais.
- **Usuário padrão:** `pedro@chegae.dev`.
- **Variáveis opcionais:**
  - `K6_USER_EMAIL`: troca o usuário de teste;
  - `K6_EVENT_ID`: fixa o evento usado no teste.
- **Perfil de carga padrão:**
  - carga leve: 15s até 3 usuários virtuais;
  - carga média: 20s até 8 usuários virtuais;
  - carga alta: 20s até 15 usuários virtuais;
  - redução gradual: 15s até 0 usuários virtuais.

### Resultados

| Métrica | Valor |
| --- | ---: |
| Latência média | 34,66 ms |
| p90 | 41,56 ms |
| p95 | 45,30 ms |
| Vazão média | 5,86 req/s |
| Total de requisições HTTP | 412 |
| Escritas realizadas no corpo principal do teste | 410 |
| Usuários virtuais máximos | 15 |
| Taxa de erro | 0,00% |

### Análise dos resultados

**Latência surpreendentemente baixa.** Os 34,66 ms de média para uma operação de escrita que envolve múltiplas validações (usuário, evento, inscrição) e retorna o evento completo atualizado são um resultado positivo, mas devem ser contextualizados: o banco local possui poucos registros, e o evento de teste tinha apenas os comentários inseridos durante o próprio teste. Em produção, eventos populares com centenas de comentários resultarão em payloads de resposta significativamente maiores.

**Dispersão contida.** A razão p95/média é de ~1,31× (45,30 ms / 34,66 ms), praticamente idêntica à do serviço de leitura. Isso indica que o comportamento de tail latency é consistente entre os dois endpoints no ambiente testado.

**Crescimento do payload ao longo do teste.** Como o endpoint retorna o evento completo após cada inserção, o payload de resposta cresce a cada comentário gravado. Com 410 inserções no mesmo evento (ou conjunto de eventos), é possível que os últimos VUs do teste já enfrentem respostas maiores que os primeiros. Esse efeito não é visível nas métricas globais, mas pode ser detectado comparando a latência média da primeira e da última janela de 1min — a tabela de métricas por intervalo pode evidenciar esse drift caso a execução seja mais longa.

**Baixa vazão absoluta.** Os 5,86 req/s com 15 VUs indicam que cada VU tem um think time relativamente alto no script. Assim como no serviço de leitura, o backend não está saturado — o perfil de carga é conservador.

**Conformidade de dados.** As 412 requisições HTTP totais com 410 escritas confirmadas (as 2 restantes são de setup/teardown) demonstram que não houve perda silenciosa de dados ou rollback inesperado.

### Hipóteses de gargalos

- Cada comentário realiza validação de usuário, evento e inscrição antes da escrita, gerando ao menos 3 queries de leitura por inserção.
- O endpoint salva o comentário e depois retorna o evento completo atualizado, incluindo todos os comentários — o custo de resposta cresce continuamente com o volume acumulado.
- A resposta fica maior conforme a quantidade de comentários do evento cresce, impactando serialização e transferência de rede.
- Escritas concorrentes no mesmo evento podem disputar conexões do pool e gerar contenção em nível de linha no PostgreSQL (row-level locking).
- O índice `idx_comentario_evento_data` ajuda a leitura ordenada dos comentários, mas o volume retornado ainda cresce continuamente.

---

## Recomendações de melhorias e otimizações

As recomendações abaixo são organizadas por impacto estimado e complexidade de implementação.

### Alta prioridade

#### 1. Paginação no endpoint `GET /api/eventos`

**Problema:** a listagem retorna todos os eventos sem paginação. Conforme o volume de eventos cresce, o payload aumenta, a serialização JSON demora mais e o tempo de transferência na rede cresce.

**Recomendação:** implementar paginação via `Pageable` do Spring Data, adicionando parâmetros `?page=0&size=20` à query. Isso limita o payload por requisição independentemente do volume total do banco.

```java
// Exemplo de assinatura no repositório
Page<Evento> findAllByOrderByCriadoEmDesc(Pageable pageable);
```

**Impacto esperado:** redução linear do tamanho do payload e do número de queries de inscrições/comentários por chamada.

#### 2. Separar retorno do `POST /api/eventos/{id}/comentarios`

**Problema:** o endpoint de inserção de comentário retorna o evento completo atualizado, o que acopla o custo de uma escrita simples ao custo crescente de leitura do evento inteiro.

**Recomendação:** retornar apenas o comentário criado (HTTP 201 Created com o DTO do comentário), deixando a atualização do feed de eventos a cargo de uma chamada explícita de leitura pelo cliente.

**Impacto esperado:** latência de escrita estabilizada independentemente do volume de comentários acumulados no evento.

#### 3. Eliminar o problema N+1 na montagem do DTO de eventos

**Problema:** a montagem do DTO consulta inscrições e comentários por evento individualmente, o que pode gerar múltiplas queries adicionais por evento retornado.

**Recomendação:** usar `JOIN FETCH` ou projeções do Spring Data para buscar inscrições e comentários de todos os eventos em uma única query, ou usar `@EntityGraph` para controlar o carregamento.

**Impacto esperado:** redução expressiva do número total de queries por requisição, especialmente quando há muitos eventos.

### Média prioridade

#### 4. Ajustar o `maximumPoolSize` do HikariCP

**Problema:** com o pool padrão de 10 conexões e 30 VUs simultâneos, há contenção de conexões nas fases de pico, contribuindo para a dispersão de latência.

**Recomendação:** ajustar o `maximumPoolSize` para um valor entre 20 e 30 no `application.properties`, levando em conta o `max_connections` do PostgreSQL (padrão 100):

```properties
spring.datasource.hikari.maximum-pool-size=25
spring.datasource.hikari.minimum-idle=10
```

**Impacto esperado:** redução da dispersão de latência (p95 mais próximo da média) em cargas de pico.

#### 5. Indexar `criadoEm` na tabela `evento`

**Problema:** a ordenação por `criadoEm DESC` na listagem de eventos percorre a tabela completa se não houver índice nessa coluna.

**Recomendação:** criar o índice caso não exista:

```sql
CREATE INDEX IF NOT EXISTS idx_evento_criado_em ON evento (criado_em DESC);
```

**Impacto esperado:** melhoria no tempo de ordenação conforme o volume da tabela cresce.

#### 6. Expandir o teste de comentários para volume acumulado

**Problema:** o teste atual foi executado com banco de dados pequeno, não capturando a degradação por volume acumulado de comentários.

**Recomendação:** implementar um script de seed que popule o banco com comentários históricos antes da execução do teste de carga, simulando um evento já em produção com centenas ou milhares de comentários.

### Baixa prioridade / trabalhos futuros

#### 7. Testar com banco de dados em servidor separado

O PostgreSQL rodando no mesmo host que o backend e o k6 compete por CPU e memória, artificialmente limitando o throughput observado. Em ambiente de staging com banco separado, os números de latência e vazão devem melhorar.

#### 8. Habilitar cache de segundo nível (Hibernate L2)

Para a listagem de eventos, que é uma operação de leitura com alta frequência relativa, habilitar cache de segundo nível do Hibernate (ex.: com Caffeine ou Redis) pode reduzir drasticamente o número de queries ao banco em cenários de leitura repetida.

#### 9. Adicionar teste de stress e soak

Os perfis atuais cobrem carga controlada (até 30 VUs / 70s). Adicionar:
- **Stress test:** aumentar VUs progressivamente até identificar o ponto de falha;
- **Soak test:** manter carga estável por 30–60 minutos para detectar vazamentos de memória ou degradação gradual.

---

## Limpeza dos dados de teste

Os scripts de escrita gravam comentários com prefixo `k6-test`. Para limpar os comentários de carga no banco local de desenvolvimento:

```powershell
docker exec chegae-postgres psql -U chegae -d chegae -c "delete from comentario where texto like 'k6-test%';"
```

---

## Relatórios

Os relatórios gerados ficam em:

- `docs/sla/medicoes-sla-testes-carga.pdf`
- `docs/sla/medicoes-sla-relatorio.html`
- `docs/sla/reports/eventos-feed.html`
- `docs/sla/reports/eventos-feed-dashboard.html`
- `docs/sla/reports/eventos-feed-latency.png`
- `docs/sla/reports/eventos-feed-latency.svg`
- `docs/sla/reports/eventos-feed-summary.json`
- `docs/sla/reports/eventos-feed-timeseries.jsonl`
- `docs/sla/reports/interval-metrics.md`
- `docs/sla/reports/comentarios-write.html`
- `docs/sla/reports/comentarios-write-dashboard.html`
- `docs/sla/reports/comentarios-write-latency.png`
- `docs/sla/reports/comentarios-write-latency.svg`
- `docs/sla/reports/comentarios-write-summary.json`
- `docs/sla/reports/comentarios-write-timeseries.jsonl`
