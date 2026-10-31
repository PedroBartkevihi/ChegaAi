import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate } from "k6/metrics";
import { makeSummary } from "./lib/report.js";
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api";
const LOAD_PROFILE = __ENV.LOAD_PROFILE || "academic";
const REPORT_DIR = __ENV.REPORT_DIR || "tests/load/resultados";
// "site": o que o site faz ao abrir o feed (primeira página e números do topo; às vezes busca ou detalhe).
// "lista": o que o site fazia antes do #25 (GET /eventos, a lista completa). Só existe no código anterior ao #26.
const ROTEIRO = __ENV.ROTEIRO || "site";
const BUSCAS = ["futebol", "orla", "pets", "botafogo"];
const STAGES = {
  smoke: [
    { duration: "5s", target: 1 },
    { duration: "5s", target: 2 },
    { duration: "5s", target: 0 },
  ],
  academic: [
    { duration: "15s", target: 5 },
    { duration: "20s", target: 15 },
    { duration: "20s", target: 30 },
    { duration: "15s", target: 0 },
  ],
};
const selectedStages = STAGES[LOAD_PROFILE] || STAGES.academic;
const ROTAS = ROTEIRO === "lista"
  ? ["GET /eventos"]
  : ["GET /eventos/feed", "GET /eventos/feed?busca", "GET /eventos/resumo", "GET /eventos/{id}"];
const feedErrors = new Rate("eventos_feed_errors");
// Um limite por rota faz o resumo do k6 trazer os números de cada uma.
const porRota = Object.fromEntries(ROTAS.map((rota) => [`http_req_duration{name:${rota}}`, ["avg<800", "p(95)<2000"]]));
export const options = {
  scenarios: {
    eventos_feed: {
      executor: "ramping-vus",
      gracefulRampDown: "10s",
      stages: selectedStages,
      tags: { service: "eventos-feed", operation: "read" },
    },
  },
  // A marca do cenário (operation) fica só nos pedidos da carga, fora do setup.
  thresholds: {
    http_req_failed: ["rate<0.02"],
    "http_req_duration{operation:read}": ["avg<800", "p(95)<2000"],
    eventos_feed_errors: ["rate<0.02"],
    ...porRota,
  },
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "max", "count"],
};
function ler(url, nome, validar) {
  const response = http.get(url, { tags: { name: nome } });
  const ok = check(response, {
    [`${nome}: status 200`]: (res) => res.status === 200,
    [`${nome}: corpo esperado`]: (res) => {
      try {
        return validar(JSON.parse(res.body));
      } catch (e) {
        return false;
      }
    },
  });
  feedErrors.add(!ok);
  return ok ? JSON.parse(response.body) : null;
}
export function setup() {
  const rota = ROTEIRO === "lista" ? `${BASE_URL}/eventos` : `${BASE_URL}/eventos/feed`;
  const response = http.get(rota, { tags: { phase: "setup", name: "setup" } });
  check(response, { "setup leu os rolés": (res) => res.status === 200 });
  return { baseUrl: BASE_URL };
}
export default function () {
  if (ROTEIRO === "lista") {
    group("lista completa", () => {
      ler(`${BASE_URL}/eventos`, "GET /eventos", Array.isArray);
    });
  } else {
    group("abrir o feed", () => {
      const comBusca = Math.random() < 0.25;
      const busca = BUSCAS[Math.floor(Math.random() * BUSCAS.length)];
      const pagina = ler(
        `${BASE_URL}/eventos/feed?secao=proximos&pagina=0&tamanho=12${comBusca ? `&busca=${busca}` : ""}`,
        comBusca ? "GET /eventos/feed?busca" : "GET /eventos/feed",
        (corpo) => Array.isArray(corpo.items),
      );
      ler(`${BASE_URL}/eventos/resumo`, "GET /eventos/resumo", (corpo) => typeof corpo.total === "number");
      if (pagina && pagina.items.length && Math.random() < 0.25) {
        const evento = pagina.items[Math.floor(Math.random() * pagina.items.length)];
        ler(`${BASE_URL}/eventos/${evento.id}`, "GET /eventos/{id}", (corpo) => corpo.id === evento.id);
      }
    });
  }
  sleep(Math.random() * 0.8 + 0.2);
}
export function handleSummary(data) {
  return makeSummary("SLA - Leitura do feed", data, {
    service: ROTEIRO === "lista" ? "Lista completa de rolés (antes do feed paginado)" : "Feed paginado de rolés",
    operation: "Leitura",
    endpoint: ROTAS.join(", "),
    baseUrl: BASE_URL,
    profile: LOAD_PROFILE,
    stages: selectedStages,
    htmlReport: `${REPORT_DIR}/eventos-feed.html`,
    jsonReport: `${REPORT_DIR}/eventos-feed-summary.json`,
  });
}
