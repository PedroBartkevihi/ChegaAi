import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

const DEFAULT_OUTPUT = "docs/sla/reports/interval-metrics.md";
const INTERVALS_SECONDS = [1, 5, 10, 30, 60];

const services = [
  {
    name: "Listagem de eventos",
    operation: "Leitura",
    endpoint: "GET /api/eventos",
    source: "docs/sla/reports/eventos-feed-timeseries.jsonl",
  },
  {
    name: "Publicacao de comentarios",
    operation: "Insercao",
    endpoint: "POST /api/eventos/{id}/comentarios",
    source: "docs/sla/reports/comentarios-write-timeseries.jsonl",
  },
];

function parseK6Jsonl(path) {
  const points = {
    durations: [],
    requests: [],
    failures: [],
    vus: [],
  };

  const lines = readFileSync(path, "utf8").split(/\r?\n/).filter(Boolean);
  for (const line of lines) {
    let record;
    try {
      record = JSON.parse(line);
    } catch {
      continue;
    }

    if (record.type !== "Point") {
      continue;
    }

    const metric = record.metric || record.data?.metric;
    const value = Number(record.data?.value);
    const time = new Date(record.data?.time).getTime();
    const tags = record.data?.tags || {};

    if (!Number.isFinite(value) || !Number.isFinite(time)) {
      continue;
    }

    if (metric === "vus") {
      points.vus.push({ time, value });
      continue;
    }

    if (tags.phase === "setup") {
      continue;
    }

    if (metric === "http_req_duration") {
      points.durations.push({ time, value });
    } else if (metric === "http_reqs") {
      points.requests.push({ time, value });
    } else if (metric === "http_req_failed") {
      points.failures.push({ time, value });
    }
  }

  points.vus.sort((a, b) => a.time - b.time);
  return points;
}

function percentile(values, p) {
  if (!values.length) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[index];
}

function average(values) {
  if (!values.length) {
    return 0;
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function nearestVu(points, time) {
  if (!points.length) {
    return 0;
  }

  let current = points[0].value;
  for (const point of points) {
    if (point.time > time) {
      break;
    }
    current = point.value;
  }
  return current;
}

function analyzeInterval(points, intervalSeconds) {
  const allRequestTimes = [...points.durations, ...points.requests].map((point) => point.time);
  const start = Math.min(...allRequestTimes);
  const end = Math.max(...allRequestTimes);
  const intervalMs = intervalSeconds * 1000;
  const buckets = new Map();

  function ensureBucket(index) {
    if (!buckets.has(index)) {
      buckets.set(index, {
        durations: [],
        requests: 0,
        failures: 0,
        vuSamples: [],
      });
    }
    return buckets.get(index);
  }

  for (const point of points.durations) {
    ensureBucket(Math.floor((point.time - start) / intervalMs)).durations.push(point.value);
  }

  for (const point of points.requests) {
    ensureBucket(Math.floor((point.time - start) / intervalMs)).requests += point.value;
  }

  for (const point of points.failures) {
    ensureBucket(Math.floor((point.time - start) / intervalMs)).failures += point.value;
  }

  const bucketCount = Math.max(1, Math.ceil((end - start + 1) / intervalMs));
  for (let index = 0; index < bucketCount; index += 1) {
    const bucket = ensureBucket(index);
    const bucketStart = start + index * intervalMs;
    const bucketEnd = Math.min(end, bucketStart + intervalMs - 1);
    for (let time = bucketStart; time <= bucketEnd; time += 1000) {
      bucket.vuSamples.push(nearestVu(points.vus, time));
    }
  }

  const activeBuckets = [...buckets.values()].filter((bucket) => bucket.requests > 0);
  const reqRates = activeBuckets.map((bucket) => bucket.requests / intervalSeconds);
  const latencyAvgs = activeBuckets
    .filter((bucket) => bucket.durations.length)
    .map((bucket) => average(bucket.durations));
  const latencyP95s = activeBuckets
    .filter((bucket) => bucket.durations.length)
    .map((bucket) => percentile(bucket.durations, 95));
  const vuAvgs = activeBuckets.map((bucket) => average(bucket.vuSamples));
  const vuMaxes = activeBuckets.map((bucket) => Math.max(...bucket.vuSamples, 0));
  const errorRates = activeBuckets.map((bucket) => (
    bucket.requests ? bucket.failures / bucket.requests : 0
  ));

  return {
    intervalLabel: intervalSeconds === 60 ? "1min" : `${intervalSeconds}s`,
    windows: activeBuckets.length,
    latencyAvg: average(latencyAvgs),
    latencyP95Avg: average(latencyP95s),
    throughputAvg: average(reqRates),
    throughputMax: Math.max(...reqRates, 0),
    vusAvg: average(vuAvgs),
    vusMax: Math.max(...vuMaxes, 0),
    errorRateAvg: average(errorRates),
  };
}

function formatNumber(value, digits = 2) {
  return Number(value).toFixed(digits).replace(".", ",");
}

function formatRate(value) {
  return `${formatNumber(value * 100)}%`;
}

function serviceSection(service) {
  const points = parseK6Jsonl(service.source);
  const rows = INTERVALS_SECONDS.map((seconds) => analyzeInterval(points, seconds));

  const table = rows.map((row) => [
    row.intervalLabel,
    row.windows,
    `${formatNumber(row.latencyAvg)} ms`,
    `${formatNumber(row.latencyP95Avg)} ms`,
    `${formatNumber(row.throughputAvg)} req/s`,
    `${formatNumber(row.throughputMax)} req/s`,
    formatNumber(row.vusAvg),
    formatNumber(row.vusMax, 0),
    formatRate(row.errorRateAvg),
  ].join(" | "));

  return `## ${service.name}

- Tipo de operacao: ${service.operation}.
- Endpoint: \`${service.endpoint}\`.
- Serie temporal analisada: \`${service.source}\`.

| Granularidade | Janelas com requisicoes | Latencia media das janelas | p95 medio das janelas | Vazao media | Maior vazao em uma janela | Concorrencia media | Concorrencia maxima | Taxa de erro media |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
${table.map((line) => `| ${line} |`).join("\n")}
`;
}

function buildReport() {
  const generatedAt = new Date().toISOString();
  return `# Metricas por intervalo - Testes de Carga

Arquivo gerado a partir das series temporais JSONL do k6 em \`docs/sla/reports/\`.

- Gerado em: \`${generatedAt}\`.
- Granularidades exigidas: 1s, 5s, 10s, 30s e 1min.
- Observacao: chamadas de setup foram removidas do calculo de requisicoes, latencia e erros.

${services.map(serviceSection).join("\n")}
`;
}

const outputPath = process.argv[2] || DEFAULT_OUTPUT;
mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, buildReport(), "utf8");
console.log(`Metricas por intervalo geradas em ${outputPath}`);
