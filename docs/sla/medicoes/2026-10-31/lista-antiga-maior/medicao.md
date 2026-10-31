# Leitura do feed, lista completa antiga, base maior, código 7d75aea

| | |
|---|---|
| Data | 2026-10-31T13:30:23Z |
| Código medido | `7d75aea` (scripts em `79861d3`) |
| Base | 203 rolés, 899 inscrições, 116 comentários |
| Carga | perfil `academic`, até 30 usuários virtuais |
| API | backend/Dockerfile, 1 CPU, 512 MiB, pool de 5 (mínimo 1) |
| Banco e k6 | postgres:15-alpine; k6 v2.2.0 (commit/00a9a1b7f5, go1.26.5, linux/amd64) |
| Máquina | AMD Ryzen 7 5700X 8-Core Processor; Docker com 16 CPUs e 7,7 GiB, Docker Desktop (containerized) |
| Memória da API | pico de 295 MiB de 512 MiB (27 amostras), sem OOM; pausas do coletor de lixo durante a carga: 143 (5 completas), a maior de 189,8 ms |
| Consultas acima de 100 ms no banco, durante a carga | nenhuma |

| Rota | Requisições | Média (ms) | p90 (ms) | p95 (ms) | Máximo (ms) |
|---|---:|---:|---:|---:|---:|
| Todas | 491 | 1275,2 | 2603,8 | 2745,3 | 4479,5 |
| GET /eventos | 491 | 1275,2 | 2603,8 | 2745,3 | 4479,5 |

| Abertura (grupo do k6, soma dos pedidos) | Aberturas | Média (ms) | p90 (ms) | p95 (ms) | Máximo (ms) |
|---|---:|---:|---:|---:|---:|
| lista completa | 491 | 1287,5 | 2618,5 | 2758,5 | 4495,0 |

Vazão média: 6,9 req/s. Erros: 0,00 %. Recebido por iteração: 116,8 KB. Limites do SLA: falharam `http_req_duration{operation:read}` avg<800, `http_req_duration{operation:read}` p(95)<2000, `http_req_duration{name:GET /eventos}` avg<800, `http_req_duration{name:GET /eventos}` p(95)<2000, `group_duration{group:::lista completa}` avg<800, `group_duration{group:::lista completa}` p(95)<2000.

![Latência e usuários virtuais por segundo](latencia.svg)
