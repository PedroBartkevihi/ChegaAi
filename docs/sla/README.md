# MEDIÇÕES DO SLA

## Nome do Serviço 1: Listagem de Eventos
* **Tipo de operações:** leitura
* **Arquivos envolvidos:**
  * [`backend/src/main/java/chegae/controller/EventoController.java`](../../backend/src/main/java/chegae/controller/EventoController.java)
  * [`backend/src/main/java/chegae/service/EventoService.java`](../../backend/src/main/java/chegae/service/EventoService.java)
  * [`backend/src/main/java/chegae/repository/EventoRepository.java`](../../backend/src/main/java/chegae/repository/EventoRepository.java)
  * [`backend/src/main/java/chegae/repository/InscricaoRepository.java`](../../backend/src/main/java/chegae/repository/InscricaoRepository.java)
  * [`backend/src/main/java/chegae/repository/ComentarioRepository.java`](../../backend/src/main/java/chegae/repository/ComentarioRepository.java)
* **Arquivos com o código fonte de medição do SLA:**
  * [`tests/load/eventos-feed.js`](../../tests/load/eventos-feed.js)
* **Descrição das configurações:**
  * **Sistema V1 (Base):** Microsoft Windows 11 Pro executado localmente. Engine de banco de dados PostgreSQL 15 instanciada via Docker Compose dividindo ciclos de CPU e barramento de memória I/O com a aplicação Spring Boot e o executor k6 no mesmo host.
  * **Sistema V2 (Otimizado):** Sistema Operacional Fedora Linux nativo. PostgreSQL 15 rodando em ambiente de container Docker isolado, permitindo acesso direto aos recursos do Kernel do Linux e isolamento de threads de execução concorrentes.

### MEDIÇÃO 1
* **Data da medição:** 01/06/2026
* **Testes de carga (SLA):**
  * **Latência Média:** 62,81 ms
  * **Percentil p95:** 83,05 ms
  * **Vazão Média (Throughput):** 19,35 req/s (degradando de forma acentuada para 3,10 req/s sob rampa máxima de estresse).
  * **Concorrência Máxima:** Estágio incremental atingindo até 30 Usuários Virtuais (VUs) simultâneos.
* **Potenciais gargalos do sistema:**
  * **Pool Exhaustion:** Uso dos limites padrão do HikariCP (`maximum-pool-size=10`). Com 30 VUs em concorrência, o pool saturava por completo aos 35 segundos de teste, forçando o enfileiramento síncrono de requisições HTTP e empurrando a latência de pico para **350,00 ms**.
  * **Thread Starvation:** O escalonamento bloqueante de threads do servidor Tomcat no ambiente Windows gerava alto overhead de chaveamento de contexto, derrubando o rendimento volumétrico de processamento.

### MEDIÇÃO 2
* **Data da medição:** 08/06/2026
* **Testes de carga (SLA):**
  * **Latência Média:** 7,93 ms (*single-digit* de alta performance).
  * **Percentil p95:** 10,25 ms estável ao longo de toda a rampa de carga.
  * **Vazão Média (Throughput):** 21,40 req/s estáveis (atingindo patamar linear estável de **23,80 req/s** sob carga limite sem qualquer degradação).
  * **Concorrência Máxima:** Estágio incremental atingindo até 30 Usuários Virtuais (VUs) simultâneos.

### GRÁFICOS comparativos das medições feitas
* **Comportamento Temporal de Latência (GET):**
  ![Latência Comparativa GET](reports/eventos-feed-latency-v2.png)
* **Comportamento e Capacidade Volumétrica de Vazão (RPS):**
  ![Vazão Comparativa GET](reports/eventos-feed-vazao-v2.png)

### Melhorias/otimizações
* Redimensionamento preventivo do pool HikariCP e imposição de restrição de concorrência máxima de processamento no Tomcat para estabilizar o consumo de recursos e evitar *Context Switching*.
* **Arquivos modificados:**
  * [`backend/src/main/resources/application.properties`](../../backend/src/main/resources/application.properties)

---

## Nome do Serviço 2: Publicação de Comentários
* **Tipo de operações:** inserção
* **Arquivos envolvidos:**
  * [`backend/src/main/java/chegae/controller/EventoController.java`](../../backend/src/main/java/chegae/controller/EventoController.java)
  * [`backend/src/main/java/chegae/service/ComentarioService.java`](../../backend/src/main/java/chegae/service/ComentarioService.java)
  * [`backend/src/main/java/chegae/repository/ComentarioRepository.java`](../../backend/src/main/java/chegae/repository/ComentarioRepository.java)
  * [`backend/src/main/java/chegae/repository/UsuarioRepository.java`](../../backend/src/main/java/chegae/repository/UsuarioRepository.java)
* **Arquivos com o código fonte de medição do SLA:**
  * [`tests/load/comentarios-write.js`](../../tests/load/comentarios-write.js)
* **Descrição das configurações:**
  * **Sistema V1 (Base):** Host local baseado em processador AMD Ryzen 5 2400G com sistema operacional Windows 11 Pro. Operações de persistência relacional executadas sem estratégias de isolamento de mapeamento de memória ORM.
  * **Sistema V2 (Otimizado):** Host baseado em processador AMD Ryzen 7 5700X rodando Fedora Linux, aproveitando otimizações nativas de I/O de disco assíncronas do subsistema de armazenamento.

### MEDIÇÃO 1
* **Data da medição:** 01/06/2026
* **Testes de carga (SLA):**
  * **Latência Média:** 34,66 ms (sofrendo picos extremos de **520,40 ms**).
  * **Percentil p95:** 45,30 ms (com dispersão acumulada estourando para **1250,00 ms** nos minutos críticos de contenção).
  * **Vazão Média (Throughput):** 5,86 req/s
  * **Concorrência Máxima:** Rampa incremental operando com até 15 Usuários Virtuais (VUs) simultâneos.
* **Potenciais gargalos do sistema:**
  * **Problema de Consultas Redundantes (N+1 SQL):** O fluxo interno de negócio utilizava buscas completas via métodos síncronos `findById` para validar entidades lógicas antes de persistir o comentário. Isto gerava um tráfego redundante de tabelas completas para a memória Heap do Spring Boot e disparava travas de concorrência por linhas (*row-level locking*) no PostgreSQL.

### MEDIÇÃO 2
* **Data da medição:** 08/06/2026
* **Testes de carga (SLA):**
  * **Latência Média:** 18,15 ms de forma totalmente estável e previsível.
  * **Percentil p95:** 42,30 ms controlados, sem desvios padrão ou anomalias.
  * **Vazão Média (Throughput):** 8,35 req/s (permitindo ao ecossistema saltar de 412 escritas originais para **585** inserções concluídas com sucesso).
  * **Concorrência Máxima:** Rampa incremental operando com até 15 Usuários Virtuais (VUs) simultâneos.

### GRÁFICOS comparativos das medições feitas
* **Comportamento Temporal de Latência na Escrita (POST):**
  ![Latência Comparativa POST](reports/comentarios-write-latency-v2.png)

### Melhorias/otimizações
* Eliminação de queries síncronas através da aplicação de validação rápida com `existsById` e carregamento leve via Lazy Proxies do JPA (`getReferenceById`), permitindo a vinculação direta da chave estrangeira (FK) no comando `INSERT` sem efetuar varreduras prévias na base.
* **Arquivos modificados:**
  * [`backend/src/main/java/chegae/service/ComentarioService.java`](../../backend/src/main/java/chegae/service/ComentarioService.java)