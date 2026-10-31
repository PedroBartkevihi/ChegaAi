# Testes de carga (k6)

## Scripts

- **`eventos-feed.js`** (leitura): repete o que o site faz ao abrir o feed. Pede a primeira página de `GET /eventos/feed` (uma vez em quatro com busca) e `GET /eventos/resumo`; uma vez em quatro, abre também o detalhe `GET /eventos/{id}`. Com `ROTEIRO=lista`, mede a antiga `GET /eventos` (lista completa), que só existe no código anterior ao PR #26; serve para comparar o antes e o depois do feed paginado.
- **`comentarios-write.js`** (escrita): no setup, cria pela API uma conta e um rolé próprios (quem organiza já pode comentar) e então publica comentários em `POST /eventos/{id}/comentarios`. No fim, apaga o rolé.

Perfis (`LOAD_PROFILE`):

- `smoke`: 15 s, até 2 usuários virtuais. É o que o CI roda.
- `academic`: 70 s, até 30 usuários virtuais na leitura e 15 na escrita.

Limites do SLA (thresholds do k6), contados só nos pedidos da carga, sem o setup:

- **Leitura:** média abaixo de 800 ms e p95 abaixo de 2 s, no total e em cada rota; erros abaixo de 2 %.
- **Escrita:** média abaixo de 1,2 s e p95 abaixo de 3 s; erros abaixo de 5 %.

## Medir

O `medir.sh` monta um ambiente descartável parecido com o Cloud Run, roda um script e desmonta tudo. Precisa de Docker e de bash (no Windows, o Git Bash), além do Node para gravar os metadados.

```bash
tests/load/medir.sh --script eventos-feed --base maior
```

Opções:

- `--script`: `eventos-feed` (padrão) ou `comentarios-write`.
- `--perfil`: `academic` (padrão) ou `smoke`.
- `--base`: `demonstracao` (padrão, 7 rolés) ou `maior` (203 rolés, de [`base-maior.sql`](base-maior.sql)).
- `--roteiro`: `site` (padrão) ou `lista`.
- `--versao`: o commit a medir (padrão `HEAD`).
- `--saida`: a pasta do resultado.
- `--porta`: a porta local da API (padrão `18095`).

Passo a passo:

1. Gera a imagem da API pelo [`backend/Dockerfile`](../../backend/Dockerfile), a partir do commit pedido.
2. Sobe um PostgreSQL 15 e a API num contêiner com 1 CPU e 512 MiB, com pool de 5 conexões (mínimo 1), como no Cloud Run. Só nessa API local os limites de cadastro, criação e comentário ficam altos.
3. Carrega a base, faz um aquecimento curto e roda o k6 pela imagem oficial `grafana/k6:2.2.0`.
4. Mede a memória da API a cada segundo e confere se o contêiner morreu por falta de memória (OOM).

Na primeira vez, o Docker baixa as imagens base do `Dockerfile` e a do k6.

O resultado fica numa pasta em `tests/load/resultados/`, que o Git ignora. Ela traz:

- a série do k6 (`serie.jsonl`);
- o resumo e o relatório HTML do k6;
- `memoria.txt` e `api.log`;
- `metadados.json`, com data, commit, base, ambiente, pico de memória e o código de saída do k6.

## Relatório

O gerador monta as tabelas e o gráfico só com os dados da execução:

```bash
node tests/load/tools/relatorio.mjs medicao tests/load/resultados/<pasta> docs/sla/medicoes/<nome>
node tests/load/tools/relatorio.mjs comparar docs/sla/medicoes/comparacao.md docs/sla/medicoes/<a> docs/sla/medicoes/<b>
```

O modo `medicao` escreve, na pasta de destino:

- `medicao.md`: o ambiente e a tabela por rota, com média, p90, p95 e máximo;
- `latencia.svg`: média, p95 e usuários virtuais por segundo;
- `por-segundo.csv`;
- `resumo.json`, com as métricas do k6;
- uma cópia dos `metadados.json`.

O modo `comparar` junta várias medições numa tabela.

As ferramentas `tools/generate-k6-chart.mjs` e `tools/generate-interval-metrics.mjs` são as que geraram os relatórios da disciplina, em `docs/sla`.

## Limites

Os números do PC não são os da produção:

- **Banco:** em produção, o Neon fica do outro lado da rede.
- **CPU e disco:** o contêiner limita a quantidade de CPU e de memória, mas a velocidade de cada núcleo e o disco continuam sendo os do PC.
- **Primeira visita:** em produção, ela paga o arranque a frio do Cloud Run.
