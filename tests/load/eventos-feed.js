import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import { makeSummary } from "./lib/report.js";
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api";
const LOAD_PROFILE = __ENV.LOAD_PROFILE || "academic";
const REPORT_DIR = __ENV.REPORT_DIR || "docs/sla/reports";
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
const feedErrors = new Rate("eventos_feed_errors");
const feedLatency = new Trend("eventos_feed_latency", true);
export const options = {
  scenarios: {
    eventos_feed: {
      executor: "ramping-vus",
      gracefulRampDown: "10s",
      stages: selectedStages,
      tags: { service: "eventos-feed", operation: "read" },
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.02"],
    http_req_duration: ["avg<800", "p(95)<2000"],
    eventos_feed_errors: ["rate<0.02"],
    eventos_feed_latency: ["avg<600", "p(95)<1500"],
  },
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "max", "count"],
};
export function setup() {
  const response = http.get(`${BASE_URL}/eventos/feed`, {
    tags: { phase: "setup", service: "eventos-feed" },
  });
  check(response, {
    "setup leu o feed": (res) => res.status === 200,
  });
  return {
    baseUrl: BASE_URL,
  };
}
export default function () {
  group("GET /eventos/feed", () => {
    const response = http.get(`${BASE_URL}/eventos/feed?secao=proximos&pagina=0&tamanho=12`, {
      tags: { service: "eventos-feed", endpoint: "GET /eventos/feed" },
    });
    feedLatency.add(response.timings.duration);
    const ok = check(response, {
      "status 200": (res) => res.status === 200,
      "resposta em JSON": (res) => res.headers["Content-Type"] && res.headers["Content-Type"].includes("application/json"),
      "payload e pagina": (res) => {
        try {
          return Array.isArray(JSON.parse(res.body).items);
        } catch (e) {
          return false;
        }
      },
    });
    feedErrors.add(!ok);
  });
  sleep(Math.random() * 0.8 + 0.2);
}
export function handleSummary(data) {
  return makeSummary("SLA - Listagem de eventos", data, {
    service: "Listagem de eventos",
    operation: "Leitura",
    endpoint: "GET /api/eventos/feed",
    baseUrl: BASE_URL,
    profile: LOAD_PROFILE,
    stages: selectedStages,
    htmlReport: `${REPORT_DIR}/eventos-feed.html`,
    jsonReport: `${REPORT_DIR}/eventos-feed-summary.json`,
  });
}
