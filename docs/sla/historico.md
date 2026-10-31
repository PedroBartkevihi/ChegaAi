# MEDIÇÕES DO SLA — EVOLUÇÃO E OTIMIZAÇÃO (FASE E)

## Nome do Serviço 1: Listagem e Feed de Eventos
* **Tipo de operações:** Leitura
* **Arquivos envolvidos:**
  * [`backend/src/main/java/chegaai/controller/EventoController.java`](../../backend/src/main/java/chegaai/controller/EventoController.java)
  * [`backend/src/main/java/chegaai/service/EventoService.java`](../../backend/src/main/java/chegaai/service/EventoService.java)
  * [`backend/src/main/java/chegaai/repository/EventoRepository.java`](../../backend/src/main/java/chegaai/repository/EventoRepository.java)
* **Arquivos com o código fonte de medição do SLA:**
  * [`tests/load/eventos-feed.js`](../../tests/load/eventos-feed.js)
* **Observação (30/10/2026):** as medições abaixo foram feitas na antiga `GET /api/eventos`, que devolvia a lista completa e foi removida. O `eventos-feed.js` passou a medir a primeira página de `GET /api/eventos/feed`.
* **Descrição das configurações:**
  * **MEDIÇÃO 1 (Base):** Microsoft Windows 11 Pro executado localmente. Engine de banco de dados PostgreSQL 15 instanciada via Docker Compose dividindo ciclos de CPU e barramento de memória I/O com a aplicação Spring Boot e o executor k6 no mesmo host.
  * **MEDIÇÃO 2 (Otimizado):** Sistema Operacional Fedora Linux nativo. PostgreSQL 15 rodando em ambiente de container Docker isolado, permitindo acesso direto aos recursos do Kernel do Linux e isolamento de threads de execução concorrentes. Código-fonte compactado e sem comentários.

### MEDIÇÃO 1
* **Data da medição:** 01/06/2026
* **Testes de carga (SLA):**
  * **Latência Média:** 62.81 ms
  * **Vazão (RPS):** ~150 req/s
  * **Concorrência Máxima:** 50 usuários simultâneos estáveis.
* **Potenciais gargalos do sistema:** Alto overhead de log de consultas SQL no terminal, contenção de I/O de disco no ambiente Windows devorando ciclos do PostgreSQL e latência causada pelo parsing de blocos excessivos de comentários.

### MEDIÇÃO 2
* **Data da medição:** 09/06/2026
* **Testes de carga (SLA):**
  * **Latência Média:** 12.45 ms (Redução de ~80%)
  * **Vazão (RPS):** ~480 req/s
  * **Concorrência Máxima:** 200 usuários simultâneos sem estourar limites de timeout.
* **Melhorias/otimizações:**
  * Desativação de logs redundantes do Hibernate (`CHEGAAI_SHOW_SQL=false`).
  * Execução em ambiente dedicado nativo no Fedora Linux.
  * Minificação sintática do backend por remoção de comentários e linhas órfãs via automação Python.

---

## Nome do Serviço 2: Escrita e Persistência de Comentários
* **Tipo de operações:** Inserção
* **Arquivos envolvidos:**
  * [`backend/src/main/java/chegaai/controller/AuthController.java`](../../backend/src/main/java/chegaai/controller/AuthController.java)
  * [`backend/src/main/java/chegaai/service/ComentarioService.java`](../../backend/src/main/java/chegaai/service/ComentarioService.java)
  * [`backend/src/main/java/chegaai/repository/ComentarioRepository.java`](../../backend/src/main/java/chegaai/repository/ComentarioRepository.java)
* **Arquivos com o código fonte de medição do SLA:**
  * [`tests/load/comentarios-write.js`](../../tests/load/comentarios-write.js)
* **Descrição das configurações:** Idênticas ao Serviço 1 (Migração do ambiente host Windows 11 para Fedora Linux nativo e compactação total do ecossistema de código).

### MEDIÇÃO 1
* **Data da medição:** 01/06/2026
* **Testes de carga (SLA):**
  * **Latência P95:** 142.10 ms
  * **Vazão (RPS):** ~85 req/s
  * **Concorrência Máxima:** 30 usuários simultâneos gravando concorrentemente antes de travar o pool de conexões.
* **Potenciais gargalos do sistema:** Bloqueios de tabelas (row-locking) concorrentes no PostgreSQL exacerbados pela lentidão de escrita do Docker for Windows WSL2.

### MEDIÇÃO 2
* **Data da medição:** 09/06/2026
* **Testes de carga (SLA):**
  * **Latência P95:** 28.30 ms
  * **Vazão (RPS):** ~290 req/s
  * **Concorrência Máxima:** 150 usuários simulados injetando dados em alta intensidade.
* **Melhorias/otimizações:** Otimização da persistência de dados no Linux, eliminação total de comentários nos scripts k6 e nas classes de DTO do backend, diminuindo o tempo de JIT da JVM.

---

## 📊 GRÁFICOS COMPARATIVOS E EVOLUTIVOS
Os relatórios consolidados contendo os gráficos de linha evolutivos criados pelas ferramentas de métricas (`generate-k6-chart.mjs`) e os comparativos detalhados de ganho de performance estão arquivados e disponíveis nos links abaixo:

* 📄 **[PDF] Relatório Comparativo Atualizado (V2):** [reports/medicoes-sla-relatorio-v2.pdf](reports/medicoes-sla-relatorio-v2.pdf)
* 📜 **[PDF] Histórico da Primeira Medição (V1):** [reports/medicoes-sla-testes-carga-v1.pdf](reports/medicoes-sla-testes-carga-v1.pdf)