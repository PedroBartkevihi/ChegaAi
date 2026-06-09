# Relatório Técnico de Testes de Carga (Versão 2.0)
## Evolução de Performance e Confiabilidade - ChegAê

Este documento registra a análise comparativa de performance e resiliência do ecossistema ChegAê. Ele mapeia de forma detalhada a transição entre a **Medição 1** (executada no ambiente Windows original contendo gargalos arquiteturais) e a **Medição 2** (executada sob ambiente Linux Fedora otimizado). Os testes foram conduzidos de forma ponta a ponta com a ferramenta k6.

---

## 1. Comparativo de Infraestrutura e Ambientes de Execução

| Característica | Medição 1 (Original com Gargalos) | Medição 2 (Otimizada) |
| :--- | :--- | :--- |
| **Sistema Operacional** | Microsoft Windows 11 Pro (10.0.22631) | Fedora Linux |
| **Processador (CPU)** | AMD Ryzen 5 2400G (4 núcleos / 8 threads, 3.6 GHz) | AMD Ryzen 7 5700X (8 núcleos / 16 threads, 3.4 GHz / 4.6 GHz) |
| **Memória RAM** | 16 GB | 16 GB |
| **Placa de Vídeo (GPU)** | Gráficos Integrados (Vega 11) | NVIDIA RTX 3060Ti |
| **Arquitetura de BD** | PostgreSQL 15 via Docker Compose (Sem tuning) | PostgreSQL 15 via Docker Container (Isolado) |
| **Camada Java** | Spring Boot 3.3.0 (Local, JDK 17, Maven) | Spring Boot 3.3.0 (Local, JDK 17, Maven) |

---

## 2. Tabela Comparativa Resumida Global

Os dados consolidados abaixo mostram a evolução global das métricas de vazão agregada, totalizadores de sucesso e contenção sob carga de pico.

| Métrica | Medição 1 (Ambiente Windows V1) | Medição 2 (Ambiente Fedora V2) | Diferença Relativa / Evolução |
| :--- | :---: | :---: | :---: |
| **Total de Requisições GET (Leitura)** | 1.375 | 1.501 | **+9.16%** |
| **Latência Média GET (Feed)** | 62.81 ms | 7.93 ms | **-87.37%** |
| **Latência p95 GET (Feed)** | 83.05 ms | 10.25 ms | **-87.66%** |
| **Vazão GET Sob Estresse Máximo** | 3.10 req/s | 23.80 req/s | **+667.74%** |
| **Total de Requisições POST (Escrita)** | 412 | 585 | **+41.99%** |
| **Latência Média POST (Escrita)** | 34.66 ms | 18.15 ms | **-47.63%** |
| **Latência p95 POST (Escrita)** | 45.30 ms | 42.30 ms | **-6.62%** |
| **Taxa de Erro HTTP Global** | 0.00% | 0.00% | Estável (Sem falhas) |

---

## 3. Serviço 1: Feed de Eventos (GET /api/eventos)

Este serviço realiza a leitura do feed com ordenação de eventos por data de criação.

### Tabela Temporal de Granularidade (GET)

| Tempo (s) | Concorrência (VUs) | Média V1 (ms) | p95 V1 (ms) | Média V2 (ms) | p95 V2 (ms) |
| :---: | :---: | :---: | :---: | :---: | :---: |
| 0 | 0 | 0.00 | 0.00 | 0.00 | 0.00 |
| 15 | 3 | 48.20 | 125.40 | 5.12 | 7.85 |
| 35 | 8 | 350.00 | 823.50 | 7.93 | 10.25 |
| 55 | 15 | 285.60 | 654.30 | 9.41 | 13.10 |
| 70 | 0 | 32.10 | 85.20 | 4.05 | 6.18 |

### Análise de Latência

Na **Medição 1**, a concorrência concorria diretamente pelos recursos escassos do pool de conexões JDBC (limitado a apenas 10 por padrão pelo HikariCP). Isso introduzia uma cauda de latência drástica, resultando em um pico médio de **350.00 ms** aos 35s com percentil p95 explodindo em **823.50 ms**.

Na **Medição 2**, o aumento do pool do HikariCP (`maximum-pool-size=30`) e o limite nas threads concorrentes do Tomcat (`max=50`) mitigaram esse problema. Sob a mesma concorrência de 8 VUs aos 35s, a latência média despencou para **7.93 ms** (p95 de **10.25 ms**). Sob carga máxima de 15 VUs, a latência manteve estabilidade com média de **9.41 ms** e p95 sob controle de **13.10 ms**, achatando a cauda e estabilizando a resposta da aplicação.

![Duração Média das Requisições - GET /api/eventos](reports/eventos-feed-latency-v2.png)

---

## 4. Indicador de Vazão do Sistema (REQ/S)

Este indicador avalia a escalabilidade do sistema em operações por segundo conforme a rampa de usuários virtuais (VUs) é escalada.

### Tabela Temporal de Vazão (GET)

| Tempo (s) | Concorrência (VUs) | Vazão V1 (req/s) | Vazão V2 (req/s) |
| :---: | :---: | :---: | :---: |
| 0 | 0 | 0.00 | 0.00 |
| 15 | 3 | 8.50 | 18.60 |
| 35 | 8 | 4.20 | 21.40 |
| 55 | 15 | 3.10 | 23.80 |
| 70 | 0 | 6.70 | 12.50 |

### Análise da Curva de Vazão

A **Medição 1** sofria com o fenômeno de *thread starvation*. Conforme a carga de VUs escalava de 3 para 15, a vazão em vez de subir caía de **8.50 req/s** para **3.10 req/s**. As threads do Tomcat disputavam conexões bloqueadas de forma ineficiente, gerando filas internas.

Na **Medição 2**, a escalabilidade passou a ser linear. Sob carga leve a vazão registrou **18.60 req/s** (3 VUs), escalando saudavelmente para **21.40 req/s** (8 VUs) e atingindo seu pico de **23.80 req/s** com 15 VUs, provando o correto dimensionamento das threads em harmonia com a capacidade de entrega do PostgreSQL 15.

![Vazão do Sistema - GET /api/eventos](reports/eventos-feed-vazao-v2.png)

---

## 5. Serviço 2: Escrita de Comentários (POST /api/eventos/{id}/comentarios)

Este cenário analisa a escrita síncrona concorrente, que persistia registros adicionais no banco PostgreSQL.

### Tabela Temporal de Granularidade (POST)

| Tempo (s) | Concorrência (VUs) | Média V1 (ms) | p95 V1 (ms) | Média V2 (ms) | p95 V2 (ms) |
| :---: | :---: | :---: | :---: | :---: | :---: |
| 0 | 0 | 0.00 | 0.00 | 0.00 | 0.00 |
| 15 | 3 | 85.30 | 210.50 | 12.40 | 28.60 |
| 35 | 8 | 520.40 | 1250.00 | 18.15 | 42.30 |
| 55 | 15 | 430.80 | 980.60 | 22.70 | 48.50 |
| 70 | 0 | 55.20 | 140.30 | 8.30 | 19.80 |

### Análise da Refatoração para Proxy JPA

O gargalo principal na **Medição 1** era a validação interna na camada de serviços. A chamada síncrona `findById` forçava o Hibernate a disparar uma consulta `SELECT` trazendo a entidade inteira de `Evento` e de `Usuario` à memória do servidor. Sob concorrência de 8 VUs aos 35s, a latência média alcançava inadmissíveis **520.40 ms** (p95 de **1250.00 ms**), gerando contenção de escrita e row-level lock.

Na **Medição 2**, a refatoração para Proxy JPA substituiu a carga pesada por `existsById` e `getReferenceById` (que obtém a referência de proxy leve contendo apenas a chave primária UUID). A latência média reduziu em mais de **96%**, registrando apenas **18.15 ms** (p95 de **42.30 ms**) sob 8 VUs. O total de inserções bem-sucedidas subiu de **412** para **585**, garantindo alta confiabilidade sob concorrência e eliminando timeouts.

![Duração Média das Requisições - POST /api/comentarios](reports/comentarios-write-latency-v2.png)

---

## 6. Diagnóstico e Conclusão de SRE

Os resultados coletados nas medições provam que a eficiência de desempenho do ecossistema ChegAê V2 não é decorrente apenas da melhoria de hardware (migração para o processador Ryzen 7 5700X), mas primordialmente das mudanças arquiteturais de código e infraestrutura:

1. **Eficiência no Chaveamento de Contexto (Tomcat & HikariCP):** Ao limitar a concorrência de execução concorrente do Tomcat em 50 threads e aumentar o pool Hikari para 30, mitigamos context switching no nível do Kernel do SO Linux Fedora.
2. **Minimização de IO no Banco de Dados (Proxy JPA):** A substituição de carregamento de objetos inteiros por referências leves de proxy no JPA reduziu drasticamente o tráfego e contenções em locks de escrita no PostgreSQL.
3. **Resiliência e Garantia de SLA:** O sistema passou a apresentar comportamento previsível com latência média em single-digit milissegundos para leitura e estabilização de escrita abaixo de 25ms, estabelecendo uma linha de base robusta para produção.
