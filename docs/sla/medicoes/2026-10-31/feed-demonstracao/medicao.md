# Leitura do feed, feed paginado, base demonstracao, código b1d9b9b

| | |
|---|---|
| Data | 2026-10-31T13:28:43Z |
| Código medido | `b1d9b9b` (scripts em `79861d3`) |
| Base | 7 rolés, 31 inscrições, 4 comentários |
| Carga | perfil `academic`, até 30 usuários virtuais |
| API | backend/Dockerfile, 1 CPU, 512 MiB, pool de 5 (mínimo 1) |
| Banco e k6 | postgres:15-alpine; k6 v2.2.0 (commit/00a9a1b7f5, go1.26.5, linux/amd64) |
| Máquina | AMD Ryzen 7 5700X 8-Core Processor; Docker com 16 CPUs e 7,7 GiB, Docker Desktop (containerized) |
| Memória da API | pico de 281 MiB de 512 MiB (29 amostras), sem OOM; pausas do coletor de lixo durante a carga: 24 (0 completas), a maior de 22,8 ms |
| Consultas acima de 100 ms no banco, durante a carga | nenhuma |

| Rota | Requisições | Média (ms) | p90 (ms) | p95 (ms) | Máximo (ms) |
|---|---:|---:|---:|---:|---:|
| Todas | 3376 | 2,7 | 4,2 | 5,4 | 174,2 |
| GET /eventos/resumo | 1495 | 1,7 | 2,3 | 2,9 | 8,1 |
| GET /eventos/feed | 1115 | 3,3 | 5,2 | 6,4 | 12,4 |
| GET /eventos/{id} | 386 | 3,6 | 5,0 | 5,9 | 23,0 |
| GET /eventos/feed?busca | 380 | 3,8 | 5,2 | 6,5 | 174,2 |

| Abertura (grupo do k6, soma dos pedidos) | Aberturas | Média (ms) | p90 (ms) | p95 (ms) | Máximo (ms) |
|---|---:|---:|---:|---:|---:|
| abrir o feed | 1495 | 6,5 | 9,8 | 11,7 | 179,9 |

Vazão média: 47,7 req/s. Erros: 0,00 %. Recebido por iteração: 3,0 KB. Limites do SLA: todos dentro.

![Latência e usuários virtuais por segundo](latencia.svg)
