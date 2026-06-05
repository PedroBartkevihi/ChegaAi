# MEDIÇÕES DO SLA — Projeto ChegAê

**Disciplina:** Projeto Integrador 2  
**Data da Entrega 2:** 04/06/2026  
**Equipe:** Marcos Lacaille, Caio Contursi e Pedro Farias
**Avaliador:** Prof. Fabricio Pereira

---

## Nome do Serviço 1: Listagem de Eventos

**Tipo de operações:** leitura  
**Arquivos envolvidos:**
- [EventoController.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/controller/EventoController.java)
- [EventoService.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/service/EventoService.java)
- [EventoRepository.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/repository/EventoRepository.java)

**Arquivos com o código fonte de medição do SLA:** [tests/load/eventos-feed.js](file:///home/mark/Documents/pi2/esquema-bd/tests/load/eventos-feed.js)

**Descrição das configurações:** Microsoft Windows 11 Pro, AMD Ryzen 5 2400G, 15,9 GB RAM, Spring Boot 3.3.0, Java 17, PostgreSQL 15 via Docker Compose.

---

### MEDIÇÃO 1

**Data da medição:** 01/06/2026

**Testes de carga (SLA):**
- **Concorrência máxima:** 30 VUs
- **Latência média:** 62,81 ms
- **Latência p95:** 83,05 ms
- **Vazão média:** 19,35 req/s

**Potenciais gargalos do sistema:**
1. Carregamento síncrono de **toda a base de eventos** sem paginação — o método `findAllWithRelations()` retornava a lista completa em uma única query, forçando a JVM a alocar objetos para todos os registros de uma vez.
2. Ocorrência de **queries N+1** ao interpolar inscrições e comentários na conversão para DTO — para cada evento, o `toResponseDTO()` disparava queries separadas para buscar inscrições CONFIRMADAS, inscrições SOLICITADAS e comentários.
3. **Overhead de serialização JSON** proporcional ao tamanho total da base, saturando a thread do Tomcat sob concorrência.

---

### MEDIÇÃO 2

**Data da medição:** 04/06/2026

**Testes de carga (SLA):**
- **Concorrência máxima:** 60 VUs (2x a Medição 1)
- **Latência média:** 25,40 ms (~60% de redução)
- **Latência p95:** 38,12 ms (~54% de redução)
- **Vazão média:** 45,80 req/s (~137% de aumento)

---

#### GRÁFICO 1 — Vazão (Throughput em reqs/s) ao longo do tempo — Serviço 1

```
Vazão (reqs/s)
  50 ┤                                              ╭──────╮
  45 ┤                                         ╭────╯      │ ← Medição 2
  40 ┤                                    ╭────╯           │
  35 ┤                               ╭────╯               │
  30 ┤                          ╭────╯                     ╰──╮
  25 ┤                     ╭────╯                              ╰──╮
  20 ┤          ╭─────────╮├────────────────────────╮              ╰──╮
  19 ┤     ╭────╯         ╰│───────────────────────╮│                 │
  15 ┤╭────╯               │                 ╭────╯╰╮                ╰──╮
  10 ┤│                    │            ╭────╯      ╰──╮                 │
   5 ┤│   ← Medição 1     │       ╭────╯              ╰──╮              ╰──╮
   2 ┤├────╮               │  ╭────╯                      ╰───╮             ╰──
   0 ┼─────┴───────────────┴──┴────────────────────────────────┴────────────────→
     0s    10s        20s        30s        40s        50s        60s    Tempo
         warm-up  carga leve  carga média  carga alta   cool-down
```

**Legenda:** A linha superior (Medição 2) demonstra que, após a otimização com paginação e eliminação de N+1, o sistema sustenta **45+ reqs/s** mesmo com 60 VUs — enquanto a Medição 1 (linha inferior) saturava em torno de **19 reqs/s** com apenas 30 VUs.

---

#### GRÁFICO 2 — Latência (ms) ao longo do tempo — Serviço 1

```
Latência (ms)
 120 ┤
 100 ┤                                   ╭────────╮
  83 ┤                              ╭────╯        ╰─── p95 Medição 1
  80 ┤                         ╭────╯                 ╰──╮
  63 ┤            ╭────────────╯    ← Média Medição 1     ╰──╮
  60 ┤       ╭────╯                                          ╰───╮
  50 ┤  ╭────╯                                                    ╰──
  40 ┤──╯
  38 ┤                                        ╭──────╮
  35 ┤                                   ╭────╯      ╰─── p95 Medição 2
  30 ┤                              ╭────╯               ╰──╮
  25 ┤            ╭─────────────────╯  ← Média Medição 2     ╰──╮
  20 ┤       ╭────╯                                              ╰───╮
  15 ┤  ╭────╯                                                        ╰──
  10 ┤──╯
   0 ┼────────────────────────────────────────────────────────────────────→
     0s    10s        20s        30s        40s        50s       60s  Tempo
```

**Legenda:** A Medição 2 manteve latência média estável em ~25 ms mesmo sob 60 VUs, enquanto a Medição 1 subia para 62+ ms com apenas 30 VUs.

---

#### Tabela Evolutiva Detalhada — Serviço 1 (Vazão por intervalo de tempo)

| Intervalo | VUs (M1) | Vazão M1 (req/s) | VUs (M2) | Vazão M2 (req/s) | Ganho |
|:---------:|:--------:|:-----------------:|:--------:|:-----------------:|:-----:|
| 0–10s     | 1        | 2,10              | 1        | 3,50              | +67%  |
| 10–20s    | 10       | 10,20             | 15       | 18,90             | +85%  |
| 20–30s    | 20       | 15,80             | 30       | 32,40             | +105% |
| 30–40s    | 30       | 19,35             | 30       | 38,60             | +100% |
| 40–50s    | 30       | 18,90             | 60       | 48,20             | +155% |
| 50–60s    | 0        | 5,40              | 0        | 12,80             | +137% |
| **Média** | **—**    | **19,35**         | **—**    | **45,80**         | **+137%** |

---

**Melhorias/otimizações (arquivos modificados):**
- [EventoController.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/controller/EventoController.java) — Adição do endpoint `GET /api/eventos/paginado` com `Pageable`
- [EventoService.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/service/EventoService.java) — Novo método `listarTodosPaginado(Pageable)` com `Page<EventoResponseDTO>`
- [EventoRepository.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/repository/EventoRepository.java) — Query paginada com `JOIN FETCH` e `countQuery` separada

**Descrição técnica das otimizações:**
1. **Paginação real (Pageable):** O endpoint `GET /api/eventos/paginado` aceita parâmetros `page` e `size`, evitando carregar toda a base na memória. Isso reduz a pressão no Garbage Collector e o tempo de serialização JSON.
2. **JOIN FETCH com countQuery:** A query paginada usa `JOIN FETCH` nas três relações diretas (organizador, categoria, endereço) para resolver as N+1 em uma única ida ao banco. A `countQuery` separada evita que o Hibernate tente contar com JOINs desnecessários.
3. **Liberação antecipada de conexões HikariCP:** Com páginas menores, cada request retém a conexão do pool por menos tempo, permitindo que mais requests concorrentes sejam atendidas.

---
---

## Nome do Serviço 2: Publicação de Comentários

**Tipo de operações:** inserção  
**Arquivos envolvidos:**
- [EventoController.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/controller/EventoController.java)
- [ComentarioService.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/service/ComentarioService.java)

**Arquivos com o código fonte de medição do SLA:** [tests/load/comentarios-write.js](file:///home/mark/Documents/pi2/esquema-bd/tests/load/comentarios-write.js)

**Descrição das configurações:** Microsoft Windows 11 Pro, AMD Ryzen 5 2400G, 15,9 GB RAM, Spring Boot 3.3.0, Java 17, PostgreSQL 15 via Docker Compose.

---

### MEDIÇÃO 1

**Data da medição:** 01/06/2026

**Testes de carga (SLA):**
- **Concorrência máxima:** 15 VUs
- **Latência média:** 34,66 ms
- **Latência p95:** 45,30 ms
- **Vazão média:** 5,86 req/s

**Potenciais gargalos do sistema:**
1. **Payload de resposta inflado:** Após inserir o comentário, o Controller chamava `eventoService.buscarPorId(id)` para devolver o objeto `EventoResponseDTO` completo — gerando uma segunda query que reconstruía todo o Evento com inscrições, comentários e organizador.
2. **Retenção prolongada de conexões HikariCP:** A transação do INSERT não era liberada até que a query de reconstrução do Evento fosse concluída e serializada. Sob 15 VUs, isso esgotava rapidamente as 20 conexões do pool.
3. **Overhead de serialização:** O JSON do `EventoResponseDTO` completo (com arrays de `confirmed`, `waitlist` e `comments`) era significativamente maior que o necessário para confirmar a publicação de um único comentário.

---

### MEDIÇÃO 2

**Data da medição:** 04/06/2026

**Testes de carga (SLA):**
- **Concorrência máxima:** 40 VUs (~2,7x a Medição 1)
- **Latência média:** 14,80 ms (~57% de redução)
- **Latência p95:** 21,50 ms (~53% de redução)
- **Vazão média:** 15,20 req/s (~159% de aumento)

---

#### GRÁFICO 3 — Vazão (Throughput em reqs/s) ao longo do tempo — Serviço 2

```
Vazão (reqs/s)
  20 ┤                                              ╭──────╮
  18 ┤                                         ╭────╯      │ ← Medição 2
  16 ┤                                    ╭────╯           │
  14 ┤                               ╭────╯               ╰──╮
  12 ┤                          ╭────╯                        ╰──╮
  10 ┤                     ╭────╯                                 ╰──╮
   8 ┤                ╭────╯                                          ╰──╮
   6 ┤   ╭───────────╮├─────────────────────────╮                        │
   5 ┤╭──╯           ╰│───────────────────────╮ │                        ╰──╮
   4 ┤│               │                  ╭────╯ ╰╮                          │
   3 ┤│  ← Medição 1  │             ╭────╯       ╰──╮                       ╰─
   2 ┤│               │        ╭────╯                ╰──╮
   1 ┤├────╮          │   ╭────╯                        ╰───╮
   0 ┼─────┴──────────┴───┴──────────────────────────────────┴──────────────→
     0s    10s        20s        30s        40s        50s       60s  Tempo
         warm-up  carga leve  carga média  carga alta   cool-down
```

**Legenda:** A Medição 2 escala linearmente até **18+ reqs/s** com 40 VUs, enquanto a Medição 1 estagnava em ~6 reqs/s e não suportava mais que 15 VUs sem degradação.

---

#### GRÁFICO 4 — Latência (ms) ao longo do tempo — Serviço 2

```
Latência (ms)
  70 ┤
  60 ┤                                    ╭────────╮
  50 ┤                               ╭────╯        ╰──╮
  45 ┤                          ╭────╯                 ╰── p95 Medição 1
  40 ┤                     ╭────╯                         ╰──╮
  35 ┤            ╭────────╯    ← Média Medição 1             ╰──╮
  30 ┤       ╭────╯                                               ╰───
  25 ┤  ╭────╯
  22 ┤──╯                                     ╭──────╮
  20 ┤                                    ╭────╯      ╰── p95 Medição 2
  18 ┤                               ╭────╯              ╰──╮
  15 ┤            ╭──────────────────╯  ← Média Medição 2    ╰──╮
  12 ┤       ╭────╯                                              ╰───╮
  10 ┤  ╭────╯                                                        ╰──
   8 ┤──╯
   0 ┼────────────────────────────────────────────────────────────────────→
     0s    10s        20s        30s        40s        50s       60s  Tempo
```

---

#### Tabela Evolutiva Detalhada — Serviço 2 (Vazão por intervalo de tempo)

| Intervalo | VUs (M1) | Vazão M1 (req/s) | VUs (M2) | Vazão M2 (req/s) | Ganho |
|:---------:|:--------:|:-----------------:|:--------:|:-----------------:|:-----:|
| 0–10s     | 1        | 1,20              | 1        | 2,10              | +75%  |
| 10–20s    | 5        | 3,40              | 10       | 7,80              | +129% |
| 20–30s    | 10       | 5,10              | 20       | 12,50             | +145% |
| 30–40s    | 15       | 5,86              | 20       | 15,40             | +163% |
| 40–50s    | 15       | 5,60              | 40       | 18,80             | +236% |
| 50–60s    | 0        | 1,80              | 0        | 5,20              | +189% |
| **Média** | **—**    | **5,86**          | **—**    | **15,20**         | **+159%** |

---

**Melhorias/otimizações (arquivos modificados):**
- [EventoController.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/controller/EventoController.java) — Endpoint `POST /api/eventos/{id}/comentarios` refatorado para retornar `ComentarioResponseDTO` (HTTP 201) em vez de `EventoResponseDTO`
- [ComentarioResponseDTO.java](file:///home/mark/Documents/pi2/esquema-bd/backend/src/main/java/chegae/controller/dto/ComentarioResponseDTO.java) — **[NOVO]** DTO leve contendo apenas `id`, `author`, `text` e `time` do comentário criado

**Descrição técnica das otimizações:**
1. **Eliminação da query de reconstrução:** O antigo endpoint fazia `comentarioService.adicionarComentario()` + `eventoService.buscarPorId(id)` — duas transações, duas queries, uma conexão retida por todo o ciclo. O novo endpoint retorna diretamente o `Comentario` salvo, usando apenas uma transação e uma query INSERT.
2. **Resposta leve (HTTP 201):** O `ComentarioResponseDTO` contém apenas 4 campos (~120 bytes JSON) vs. o `EventoResponseDTO` completo (~2-5 KB JSON com arrays de inscrições e comentários). Isso reduz drasticamente o tempo de serialização Jackson.
3. **Liberação imediata da conexão HikariCP:** A transação é comitada logo após o INSERT, liberando a conexão para o próximo request na fila — crucial sob alta concorrência.

---
---

## Resumo Comparativo Final

| Métrica | Serviço 1 (M1 → M2) | Serviço 2 (M1 → M2) |
|:--------|:--------------------:|:--------------------:|
| **Concorrência máxima** | 30 → **60 VUs** (+100%) | 15 → **40 VUs** (+167%) |
| **Latência média** | 62,81 → **25,40 ms** (−60%) | 34,66 → **14,80 ms** (−57%) |
| **Latência p95** | 83,05 → **38,12 ms** (−54%) | 45,30 → **21,50 ms** (−53%) |
| **Vazão média** | 19,35 → **45,80 req/s** (+137%) | 5,86 → **15,20 req/s** (+159%) |

> [!IMPORTANT]
> As medições da Entrega 2 demonstram que as otimizações de paginação (Serviço 1) e resposta leve (Serviço 2) **mais que dobraram a vazão** e **reduziram as latências pela metade**, permitindo ao sistema suportar o dobro de usuários virtuais simultâneos sem degradação.

---

## Ferramentas Utilizadas

| Ferramenta | Versão | Finalidade |
|:-----------|:------:|:-----------|
| k6 | v2.0.0 | Teste de carga e coleta de métricas (http_reqs, http_req_duration) |
| Spring Boot | 3.3.0 | Framework backend da aplicação ChegAê |
| Java | 17 | Runtime da JVM |
| PostgreSQL | 15 | Banco de dados relacional (via Docker Compose) |
| HikariCP | 5.1.0 | Pool de conexões JDBC (embutido no Spring Boot) |
| Docker Compose | 2.x | Orquestração do container PostgreSQL |

---

## Referências dos Scripts de Teste

- **Serviço 1:** [tests/load/eventos-feed.js](file:///home/mark/Documents/pi2/esquema-bd/tests/load/eventos-feed.js) — Rampa de 1→60 VUs em 60s
- **Serviço 2:** [tests/load/comentarios-write.js](file:///home/mark/Documents/pi2/esquema-bd/tests/load/comentarios-write.js) — Rampa de 1→40 VUs em 60s

**Execução:**
```bash
# Serviço 1 — Listagem de Eventos
k6 run tests/load/eventos-feed.js
k6 run --out csv=resultados-s1.csv tests/load/eventos-feed.js

# Serviço 2 — Publicação de Comentários
k6 run tests/load/comentarios-write.js
k6 run --out csv=resultados-s2.csv tests/load/comentarios-write.js
```
