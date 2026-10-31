# Escrita de comentários, base demonstracao, código b1d9b9b

| | |
|---|---|
| Data | 2026-10-31T13:33:41Z |
| Código medido | `b1d9b9b` (scripts em `79861d3`) |
| Base | 7 rolés, 31 inscrições, 4 comentários |
| Carga | perfil `academic`, até 15 usuários virtuais |
| API | backend/Dockerfile, 1 CPU, 512 MiB, pool de 5 (mínimo 1) |
| Banco e k6 | postgres:15-alpine; k6 v2.2.0 (commit/00a9a1b7f5, go1.26.5, linux/amd64) |
| Máquina | AMD Ryzen 7 5700X 8-Core Processor; Docker com 16 CPUs e 7,7 GiB, Docker Desktop (containerized) |
| Memória da API | pico de 272 MiB de 512 MiB (29 amostras), sem OOM; pausas do coletor de lixo durante a carga: 7 (0 completas), a maior de 6,6 ms |
| Consultas acima de 100 ms no banco, durante a carga | nenhuma |

| Rota | Requisições | Média (ms) | p90 (ms) | p95 (ms) | Máximo (ms) |
|---|---:|---:|---:|---:|---:|
| Todas | 573 | 8,9 | 11,9 | 13,4 | 57,2 |

Vazão média: 8,1 req/s. Erros: 0,00 %. Recebido por iteração: 0,5 KB. Limites do SLA: todos dentro.

![Latência e usuários virtuais por segundo](latencia.svg)
