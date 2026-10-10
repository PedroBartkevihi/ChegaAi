import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import { makeSummary } from "./lib/report.js";
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api";
const LOAD_PROFILE = __ENV.LOAD_PROFILE || "academic";
const REPORT_DIR = __ENV.REPORT_DIR || "docs/sla/reports";
const USER_EMAIL = __ENV.K6_USER_EMAIL || "pedro@chegaai.dev";
const EVENT_ID = __ENV.K6_EVENT_ID || "";
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
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["avg<1200", "p(95)<3000"],
    comentarios_write_errors: ["rate<0.05"],
    comentarios_write_latency: ["avg<1000", "p(95)<2500"],
  },
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "max", "count"],
};
function login() {
  const response = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: USER_EMAIL }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { phase: "setup", service: "auth-login" },
    },
  );
  const ok = check(response, {
    "login retornou 200": (res) => res.status === 200,
    "login retornou id": (res) => Boolean(res.json("id")),
  });
  if (!ok) {
    throw new Error(`Falha no login de teste para ${USER_EMAIL}: status ${response.status}`);
  }
  return response.json();
}
function discoverWritableEvent(user) {
  if (EVENT_ID) {
    return EVENT_ID;
  }
  const response = http.get(`${BASE_URL}/eventos`, {
    tags: { phase: "setup", service: "eventos-feed" },
  });
  const ok = check(response, {
    "eventos retornou 200": (res) => res.status === 200,
  });
  if (!ok) {
    throw new Error(`Falha ao listar eventos para setup: status ${response.status}`);
  }
  const events = response.json();
  const event = events.find((item) =>
    item.status === "ABERTO"
      && (item.confirmed.includes(user.name) || item.waitlist.includes(user.name)),
  );
  if (!event) {
    throw new Error(
      `Nenhum evento aberto encontrado com inscricao para ${user.name}. Informe K6_EVENT_ID ou use outro K6_USER_EMAIL.`,
    );
  }
  return event.id;
}
export function setup() {
  const user = login();
  const eventId = discoverWritableEvent(user);
  return {
    user,
    eventId,
    baseUrl: BASE_URL,
  };
}
export default function (data) {
  group("POST /eventos/{id}/comentarios", () => {
    const body = {
      text: `k6-test comentario vu=${__VU} iter=${__ITER} ts=${Date.now()}`,
    };
    const response = http.post(
      `${BASE_URL}/eventos/${data.eventId}/comentarios`,
      JSON.stringify(body),
      {
        headers: {
          "Content-Type": "application/json",
          "X-User-Id": data.user.id,
        },
        tags: { service: "comentarios-write", endpoint: "POST /eventos/{id}/comentarios" },
      },
    );
    commentLatency.add(response.timings.duration);
    const ok = check(response, {
      "status 201": (res) => res.status === 201,
      "resposta em JSON": (res) => res.headers["Content-Type"] && res.headers["Content-Type"].includes("application/json"),
      "comentario aparece no retorno": (res) => {
        try {
          const parsed = JSON.parse(res.body);
          return parsed.text && parsed.text.includes("k6-test");
        } catch (e) {
          return false;
        }
      },
    });
    commentErrors.add(!ok);
  });
  sleep(Math.random() * 1.0 + 0.3);
}
export function handleSummary(data) {
  return makeSummary("SLA - Escrita de comentarios", data, {
    service: "Publicacao de comentarios",
    operation: "Insercao",
    endpoint: "POST /api/eventos/{id}/comentarios",
    baseUrl: BASE_URL,
    profile: LOAD_PROFILE,
    stages: selectedStages,
    htmlReport: `${REPORT_DIR}/comentarios-write.html`,
    jsonReport: `${REPORT_DIR}/comentarios-write-summary.json`,
  });
}
