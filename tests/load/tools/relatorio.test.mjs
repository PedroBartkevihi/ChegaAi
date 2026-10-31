import test from "node:test";
import assert from "node:assert/strict";
import { escala, lerSerie, metricasDoResumo, percentil, porSegundo, textoMedicao } from "./relatorio.mjs";

const ponto = (metric, segundos, value, tags = {}) => JSON.stringify({
  type: "Point", metric, data: { time: new Date(Date.UTC(2026, 9, 31, 12, 0, 0) + segundos * 1000).toISOString(), value, tags },
});

test("percentil interpola entre as amostras vizinhas", () => {
  assert.equal(percentil([], 95), null);
  assert.equal(percentil([10], 95), 10);
  assert.equal(percentil([10, 20, 30, 40, 50], 50), 30);
  assert.equal(percentil([10, 20], 95), 19.5);
});

test("os eixos do gráfico usam marcas redondas que cobrem o máximo", () => {
  assert.deepEqual(escala(43), [0, 20, 40, 60]);
  assert.deepEqual(escala(2, true), [0, 1, 2]);
  assert.deepEqual(escala(30, true), [0, 10, 20, 30]);
  assert.deepEqual(escala(0.4), [0, 0.5, 1]);
});

test("a série por segundo ignora setup e teardown e guarda os usuários virtuais", () => {
  const texto = [
    JSON.stringify({ type: "Metric", metric: "http_req_duration", data: {} }),
    ponto("vus", 0, 1),
    ponto("http_req_duration", 0.2, 900, { name: "setup", phase: "setup" }),
    ponto("http_req_duration", 0.5, 10, { name: "GET /eventos/feed" }),
    ponto("http_req_duration", 0.7, 30, { name: "GET /eventos/resumo" }),
    "linha que não é JSON",
    ponto("vus", 2, 3),
    ponto("http_req_duration", 2.1, 20, { name: "GET /eventos/feed" }),
  ].join("\n");
  const serie = lerSerie(texto);
  assert.equal(serie.duracoes.length, 3);
  const linhas = porSegundo(serie);
  assert.deepEqual(linhas.map((l) => [l.segundo, l.vus, l.requisicoes]), [[0, 1, 2], [1, 1, 0], [2, 3, 1]]);
  assert.equal(linhas[0].media, 20);
  assert.equal(linhas[1].media, null);
  assert.equal(linhas[2].p95, 20);
});

test("o resumo do k6 vira uma linha por rota e a lista de limites", () => {
  const valores = (avg, count = 10) => ({ avg, "p(90)": avg * 2, "p(95)": avg * 3, max: avg * 4, count });
  const metricas = metricasDoResumo({
    state: { testRunDurationMs: 15000 },
    metrics: {
      http_req_duration: { values: valores(900, 31) },
      "http_req_duration{operation:read}": { values: valores(5, 30), thresholds: { "avg<800": { ok: true } } },
      "http_req_duration{name:GET /eventos/resumo}": { values: valores(2, 10), thresholds: { "p(95)<2000": { ok: true } } },
      "http_req_duration{name:GET /eventos/feed}": { values: valores(8, 20), thresholds: { "p(95)<2000": { ok: false } } },
      "group_duration{group:::abrir o feed}": { values: valores(12, 15), thresholds: { "p(95)<2000": { ok: true } } },
      http_reqs: { values: { rate: 12.5, count: 31 } },
      http_req_failed: { values: { rate: 0 } },
      vus_max: { values: { max: 30 } },
      iterations: { values: { count: 15 } },
      data_received: { values: { count: 15 * 3 * 1024 } },
    },
  });
  // O total é o da carga (sem o setup), e as rotas vêm das mais pedidas para as menos.
  assert.deepEqual(metricas.rotas.map((r) => [r.rota, r.contagem]), [["Todas", 30], ["GET /eventos/feed", 20], ["GET /eventos/resumo", 10]]);
  assert.equal(metricas.rotas[0].media, 5);
  assert.equal(metricas.rotas[1].p95, 24);
  assert.equal(metricas.requisicoes, 30);
  assert.equal(metricas.vazao, 2);
  // Cada abertura do feed é um grupo do k6; o recebido por iteração vem de data_received / iterations.
  assert.deepEqual(metricas.grupos.map((g) => [g.rota, g.contagem, g.p95]), [["abrir o feed", 15, 36]]);
  assert.equal(metricas.kbPorIteracao, 3);
  assert.equal(metricas.limites.filter((l) => !l.ok).length, 1);
  const texto = textoMedicao({
    data: "2026-10-31T12:00:00Z", versaoApi: "abc1234", versaoScripts: "def5678", script: "eventos-feed", perfil: "smoke",
    roteiro: "site", base: { nome: "demonstracao", eventos: 7, inscricoes: 30, comentarios: 4 },
    ambiente: { api: "1 CPU", banco: "postgres:15-alpine", k6: "k6 v2.2.0", docker: "8 CPUs", cpuDoHost: "teste" },
    memoriaMiB: { amostras: 3, pico: 300.4, oom: false },
  }, metricas);
  assert.match(texto, /# Leitura do feed, feed paginado, base demonstracao, código abc1234/);
  assert.match(texto, /\| GET \/eventos\/feed \| 20 \| 8,0 \| 16,0 \| 24,0 \| 32,0 \|/);
  assert.match(texto, /pico de 300 MiB de 512 MiB \(3 amostras\), sem OOM/);
  assert.match(texto, /\| abrir o feed \| 15 \| 12,0 \| 24,0 \| 36,0 \| 48,0 \|/);
  assert.match(texto, /Recebido por iteração: 3,0 KB\./);
  assert.match(texto, /falharam `http_req_duration\{name:GET \/eventos\/feed\}` p\(95\)<2000/);
});
