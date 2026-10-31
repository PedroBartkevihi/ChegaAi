# Medições de SLA

Testes de carga com o [k6](https://k6.io/) contra a API, num ambiente descartável parecido com o Cloud Run. Os relatórios desta pasta saem só dos dados de cada execução: tabelas e gráficos gerados por [`tests/load/tools/relatorio.mjs`](../../tests/load/tools/relatorio.mjs), sem número digitado à mão. Como medir e o que cada script faz está em [`tests/load/README.md`](../../tests/load/README.md).

## O que foi medido (31/10/2026)

- **Leitura do feed:** repete o que o site faz ao abrir o feed (primeira página, números do topo e, às vezes, busca ou detalhe). A comparação é com a lista completa que o site pedia antes do feed paginado, no código `7d75aea`, anterior ao #23.
- **Escrita de comentários:** um participante publica comentários num rolé, com JWT.
- **Bases:** a da demonstração, com 7 rolés, e uma maior, com 203 rolés (cópias da demonstração, com 899 inscrições e 116 comentários).
- **Ambiente:**
  - API pela imagem do `backend/Dockerfile`, num contêiner com 1 CPU, 512 MiB e pool de 5 conexões, como no Cloud Run;
  - PostgreSQL 15 e k6 2.2.0, no Docker Desktop;
  - o mesmo PC (Ryzen 7 5700X), uma medição por vez.
- **Carga:** perfil `academic`, com 70 s e até 30 usuários virtuais na leitura e 15 na escrita.

## Resultados

Tabela gerada por `relatorio.mjs comparar`. Cada linha leva ao relatório completo da medição, com a tabela por rota e o gráfico por segundo.

| Medição | Requisições | Vazão (req/s) | Média (ms) | p95 (ms) | Abertura p95 (ms) | Recebido por iteração | Erros | Pico de memória | Maior pausa do GC (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| [Leitura do feed, lista completa antiga, base demonstracao, código 7d75aea](medicoes/2026-10-31/lista-antiga-demonstracao/medicao.md) | 1489 | 21,1 | 10,2 | 13,6 | 14,4 | 4,4 KB | 0,00 % | 258 MiB | 79,8 |
| [Leitura do feed, feed paginado, base demonstracao, código b1d9b9b](medicoes/2026-10-31/feed-demonstracao/medicao.md) | 3376 | 47,7 | 2,7 | 5,4 | 11,7 | 3,0 KB | 0,00 % | 281 MiB | 22,8 |
| [Leitura do feed, lista completa antiga, base maior, código 7d75aea](medicoes/2026-10-31/lista-antiga-maior/medicao.md) | 491 | 6,9 | 1275,2 | 2745,3 | 2758,5 | 116,8 KB | 0,00 % | 295 MiB | 189,8 |
| [Leitura do feed, feed paginado, base maior, código b1d9b9b](medicoes/2026-10-31/feed-maior/medicao.md) | 3318 | 46,9 | 3,1 | 6,3 | 13,7 | 6,5 KB | 0,00 % | 278 MiB | 24,5 |
| [Escrita de comentários, base demonstracao, código b1d9b9b](medicoes/2026-10-31/comentarios-demonstracao/medicao.md) | 573 | 8,1 | 8,9 | 13,4 | – | 0,5 KB | 0,00 % | 272 MiB | 6,6 |
| [Escrita de comentários, base demonstracao, código b1d9b9b](medicoes/2026-10-31/comentarios-demonstracao-repeticao/medicao.md) | 580 | 8,1 | 10,0 | 14,8 | – | 0,5 KB | 0,00 % | 270 MiB | 10,4 |
| [Escrita de comentários, base maior, código b1d9b9b](medicoes/2026-10-31/comentarios-maior/medicao.md) | 589 | 8,4 | 9,7 | 14,1 | – | 0,5 KB | 0,00 % | 270 MiB | 6,0 |

"Abertura" é o tempo de abrir o feed uma vez, somando os pedidos daquela abertura: um pedido na lista antiga; o feed e o resumo (às vezes com busca ou detalhe) no feed paginado. É a comparação justa entre os dois roteiros, porque o feed paginado faz mais pedidos, porém menores.

### O que os números mostram

- **Com 7 rolés, os dois códigos abrem o feed em pouco tempo:** p95 de 14,4 ms com a lista completa e 11,7 ms com o feed paginado.
- **Com 203 rolés, a lista completa satura a API de 1 CPU:**
  - abrir o feed passa a levar 2.758,5 ms no p95;
  - cada abertura recebe 116,8 KB;
  - a vazão cai para 6,9 req/s;
  - todos os limites do SLA falham;
  - o coletor de lixo faz 143 pausas durante a carga, 5 delas completas.
- **O feed paginado quase não muda com o tamanho da base:** com 203 rolés, o p95 da abertura é de 13,7 ms, cada abertura recebe 6,5 KB e a vazão é de 46,9 req/s. O custo passou a depender do tamanho da página, não do total de rolés.
- **Escrita de comentários:** p95 entre 13,4 e 14,8 ms nas três rodadas, sem erros.
- **Memória:** pico entre 258 e 295 MiB, de 512 MiB, sem falta de memória (OOM) em nenhuma rodada.

Limites do SLA usados nas medições (thresholds do k6):

- **Leitura:** média abaixo de 800 ms e p95 abaixo de 2 s, no total, em cada rota e em cada abertura; erros abaixo de 2 %.
- **Escrita:** média abaixo de 1,2 s e p95 abaixo de 3 s; erros abaixo de 5 %.

### Observações

- **Picos na escrita vêm do disco do PC, ampliados por um bloqueio:**
  - Em rodadas anteriores do mesmo dia (não publicadas), a escrita teve, uma ou duas vezes por rodada, pedidos de até cerca de 2 s. O log do banco mostrou a causa: um `COMMIT` lento no disco do Docker Desktop.
  - O comentário bloqueia a linha do rolé (`SELECT … FOR NO KEY UPDATE`, em `ComentarioService`), e todos os comentários da medição vão para o mesmo rolé. Então um `COMMIT` lento faz os outros comentários esperarem na fila.
  - Nas rodadas publicadas, o `COMMIT` mais lento levou 266 ms, e as pausas do GC não passaram de 11 ms.
  - Os relatórios registram as consultas acima de 100 ms e as pausas do GC de cada rodada.
- **Os números do PC não são os da produção:**
  - lá o banco (Neon) fica do outro lado da rede;
  - a velocidade de cada núcleo e o disco são os do PC, só a quantidade de CPU e de memória é limitada;
  - a primeira visita de produção paga o arranque a frio do Cloud Run, que aqui fica de fora.
- **A base maior é feita de cópias da demonstração:** a busca e o resumo podem se comportar diferente com textos e organizadores mais variados.

## Como repetir

```bash
tests/load/medir.sh --versao 7d75aea --roteiro lista --base maior
tests/load/medir.sh --versao b1d9b9b --base maior
tests/load/medir.sh --script comentarios-write --versao b1d9b9b
node tests/load/tools/relatorio.mjs medicao tests/load/resultados/<pasta> docs/sla/medicoes/<data>/<nome>
node tests/load/tools/relatorio.mjs comparar docs/sla/medicoes/<data>/comparacao.md docs/sla/medicoes/<data>/<a> docs/sla/medicoes/<data>/<b>
```

## Histórico da disciplina

As medições de junho de 2026 (V1 e V2) foram feitas durante a disciplina, ainda com o nome ChegAê. Elas ficam como registro em [`historico.md`](historico.md), com os números e as conclusões originais. No topo do arquivo há uma nota sobre o que os dados guardados não sustentam e sobre a atribuição errada de ganho à remoção de comentários do código.

Arquivos:

- o relatório V2 em [PDF](medicoes-sla-relatorio-v2.pdf), [Markdown](medicoes-sla-relatorio-v2.md) e [HTML](medicoes-sla-relatorio-v2.html);
- o relatório V1 em [PDF](../../reports/medicoes-sla-testes-carga-v1.pdf);
- os dados e gráficos daquela época, em [`reports/`](reports/).
