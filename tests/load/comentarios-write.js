import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import { makeSummary } from "./lib/report.js";
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api";
const LOAD_PROFILE = __ENV.LOAD_PROFILE || "academic";
const REPORT_DIR = __ENV.REPORT_DIR || "tests/load/resultados";
const ROTA = "POST /eventos/{id}/comentarios";
const JSON_HEADERS = { "Content-Type": "application/json" };
const STAGES = {
  smoke: [
    { duration: "5s", target: 1 },
    { duration: "5s", target: 2 },
    { duration: "5s", target: 0 },
  ],
  academic: [
    { duration: "15s", target: 3 },
    { duration: "20s", target: 8 },
    { duration: "20s", target: 15 },
    { duration: "15s", target: 0 },
  ],
};
const selectedStages = STAGES[LOAD_PROFILE] || STAGES.academic;
const commentErrors = new Rate("comentarios_write_errors");
const commentLatency = new Trend("comentarios_write_latency", true);
export const options = {
  scenarios: {
    comentarios_write: {
      executor: "ramping-vus",
      gracefulRampDown: "10s",
      stages: selectedStages,
      tags: { service: "comentarios-write", operation: "write" },
    },
  },
  // A marca do cenário (operation) fica só nos pedidos da carga, fora do setup e do teardown.
  thresholds: {
    http_req_failed: ["rate<0.05"],
    "http_req_duration{operation:write}": ["avg<1200", "p(95)<3000"],
    comentarios_write_errors: ["rate<0.05"],
    comentarios_write_latency: ["avg<1000", "p(95)<2500"],
  },
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "max", "count"],
};
function autorizado(token) {
  return { ...JSON_HEADERS, Authorization: `Bearer ${token}` };
}
// Conta e rolé próprios, criados pela API: quem organiza já fica confirmado e pode comentar.
// A API local da medição precisa dos limites de cadastro, criação e comentário elevados.
export function setup() {
  const marca = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const cadastro = http.post(`${BASE_URL}/auth/cadastro`, JSON.stringify({
    name: `Carga k6 ${marca}`,
    email: `carga-${marca}@example.invalid`,
    birthDate: "1995-05-10",
    password: `SenhaCarga-${marca}!`,
  }), { headers: JSON_HEADERS, tags: { phase: "setup", name: "POST /auth/cadastro" } });
  if (!check(cadastro, { "cadastro retornou 201": (res) => res.status === 201 })) {
    throw new Error(`Falha no cadastro da conta de carga: status ${cadastro.status}`);
  }
  const token = cadastro.json("token");
  const data = new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const evento = http.post(`${BASE_URL}/eventos`, JSON.stringify({
    title: `Rolé de carga ${marca}`,
    category: "Lazer",
    date: data,
    time: "18:00",
    cep: "",
    place: "Parque de testes, Rio de Janeiro",
    capacity: 10,
    description: "Rolé fictício criado pelo teste de carga para receber comentários.",
  }), { headers: autorizado(token), tags: { phase: "setup", name: "POST /eventos" } });
  if (!check(evento, { "rolé de carga criado": (res) => res.status === 201 })) {
    throw new Error(`Falha ao criar o rolé de carga: status ${evento.status}`);
  }
  return { token, eventId: evento.json("id") };
}
export default function (data) {
  group(ROTA, () => {
    const texto = `k6-test comentario vu=${__VU} iter=${__ITER} ts=${Date.now()}`;
    const response = http.post(
      `${BASE_URL}/eventos/${data.eventId}/comentarios`,
      JSON.stringify({ text: texto }),
      { headers: autorizado(data.token), tags: { name: ROTA } },
    );
    commentLatency.add(response.timings.duration);
    const ok = check(response, {
      "status 201": (res) => res.status === 201,
      "resposta em JSON": (res) => res.headers["Content-Type"] && res.headers["Content-Type"].includes("application/json"),
      "comentario aparece no retorno": (res) => {
        try {
          return JSON.parse(res.body).text === texto;
        } catch (e) {
          return false;
        }
      },
    });
    commentErrors.add(!ok);
  });
  sleep(Math.random() * 1.0 + 0.3);
}
// Apaga o rolé de carga, com os comentários dele.
export function teardown(data) {
  http.del(`${BASE_URL}/eventos/${data.eventId}`, null, { headers: autorizado(data.token), tags: { phase: "teardown", name: "DELETE /eventos/{id}" } });
}
export function handleSummary(data) {
  return makeSummary("SLA - Escrita de comentarios", data, {
    service: "Publicacao de comentarios",
    operation: "Insercao",
    endpoint: `${ROTA.replace("/eventos", "/api/eventos")}`,
    baseUrl: BASE_URL,
    profile: LOAD_PROFILE,
    stages: selectedStages,
    htmlReport: `${REPORT_DIR}/comentarios-write.html`,
    jsonReport: `${REPORT_DIR}/comentarios-write-summary.json`,
  });
}
