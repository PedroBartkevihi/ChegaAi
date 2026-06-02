# Metricas por intervalo - Testes de Carga

Arquivo gerado a partir das series temporais JSONL do k6 em `docs/sla/reports/`.

- Gerado em: `2026-06-02T01:02:58.722Z`.
- Granularidades exigidas: 1s, 5s, 10s, 30s e 1min.
- Observacao: chamadas de setup foram removidas do calculo de requisicoes, latencia e erros.

## Listagem de eventos

- Tipo de operacao: Leitura.
- Endpoint: `GET /api/eventos`.
- Serie temporal analisada: `docs/sla/reports/eventos-feed-timeseries.jsonl`.

| Granularidade | Janelas com requisicoes | Latencia media das janelas | p95 medio das janelas | Vazao media | Maior vazao em uma janela | Concorrencia media | Concorrencia maxima | Taxa de erro media |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1s | 70 | 67,45 ms | 82,97 ms | 19,63 req/s | 50,00 req/s | 12,99 | 30 | 0,00% |
| 5s | 14 | 66,87 ms | 111,45 ms | 19,63 req/s | 44,20 req/s | 12,99 | 30 | 0,00% |
| 10s | 7 | 66,09 ms | 102,07 ms | 19,63 req/s | 41,00 req/s | 12,99 | 30 | 0,00% |
| 30s | 3 | 60,83 ms | 81,12 ms | 15,27 req/s | 31,97 req/s | 12,92 | 30 | 0,00% |
| 1min | 2 | 59,73 ms | 78,36 ms | 11,45 req/s | 20,20 req/s | 12,87 | 30 | 0,00% |

## Publicacao de comentarios

- Tipo de operacao: Insercao.
- Endpoint: `POST /api/eventos/{id}/comentarios`.
- Serie temporal analisada: `docs/sla/reports/comentarios-write-timeseries.jsonl`.

| Granularidade | Janelas com requisicoes | Latencia media das janelas | p95 medio das janelas | Vazao media | Maior vazao em uma janela | Concorrencia media | Concorrencia maxima | Taxa de erro media |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1s | 68 | 35,38 ms | 39,36 ms | 6,03 req/s | 15,00 req/s | 6,88 | 15 | 0,00% |
| 5s | 14 | 35,29 ms | 49,44 ms | 5,86 req/s | 11,80 req/s | 6,71 | 15 | 0,00% |
| 10s | 7 | 35,26 ms | 57,23 ms | 5,86 req/s | 11,80 req/s | 6,71 | 15 | 0,00% |
| 30s | 3 | 34,88 ms | 46,12 ms | 4,56 req/s | 9,53 req/s | 6,73 | 15 | 0,00% |
| 1min | 2 | 35,22 ms | 43,76 ms | 3,42 req/s | 6,07 req/s | 6,75 | 15 | 0,00% |

