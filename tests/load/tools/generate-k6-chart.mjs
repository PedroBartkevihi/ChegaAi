import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname } from "node:path";
const [, , inputPath, outputPath, ...titleParts] = process.argv;
if (!inputPath || !outputPath) {
  console.error("Uso: node tests/load/tools/generate-k6-chart.mjs <k6-jsonl> <saida.svg> [titulo]");
  process.exit(1);
}
const title = titleParts.join(" ") || "Duracao media das requisicoes ao longo do tempo";
const lines = readFileSync(inputPath, "utf8")
  .split(/\r?\n/)
  .filter(Boolean);
const requestPoints = [];
const vuPoints = [];
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
  if (!Number.isFinite(value) || !Number.isFinite(time)) {
    continue;
  }
  if (metric === "http_req_duration") {
    requestPoints.push({ time, value });
  } else if (metric === "vus") {
    vuPoints.push({ time, value });
  }
}
if (!requestPoints.length) {
  throw new Error(`Nenhum ponto http_req_duration encontrado em ${inputPath}`);
}
const start = Math.min(...requestPoints.map((point) => point.time));
const end = Math.max(...requestPoints.map((point) => point.time));
const durationSeconds = Math.max(1, Math.ceil((end - start) / 1000));
const buckets = new Map();
for (const point of requestPoints) {
  const second = Math.floor((point.time - start) / 1000);
  const bucket = buckets.get(second) || { sum: 0, count: 0 };
  bucket.sum += point.value;
  bucket.count += 1;
  buckets.set(second, bucket);
}
const latencySeries = Array.from({ length: durationSeconds + 1 }, (_, second) => {
  const bucket = buckets.get(second);
  return {
    second,
    value: bucket ? bucket.sum / bucket.count : null,
  };
});
const sortedDurations = requestPoints.map((point) => point.value).sort((a, b) => a - b);
function percentile(p) {
  if (!sortedDurations.length) return 0;
  const index = Math.min(sortedDurations.length - 1, Math.ceil((p / 100) * sortedDurations.length) - 1);
  return sortedDurations[index];
}
function nearestVuValue(second) {
  if (!vuPoints.length) {
    return 0;
  }
  const target = start + second * 1000;
  let nearest = vuPoints[0];
  for (const point of vuPoints) {
    if (point.time <= target) {
      nearest = point;
    } else {
      break;
    }
  }
  return nearest.value;
}
vuPoints.sort((a, b) => a.time - b.time);
const vuSeries = Array.from({ length: durationSeconds + 1 }, (_, second) => ({
  second,
  value: nearestVuValue(second),
}));
const p95 = percentile(95);
const p99 = percentile(99);
const maxLatency = Math.max(...latencySeries.map((point) => point.value || 0), p95, p99);
const maxVu = Math.max(...vuSeries.map((point) => point.value), 1);
const width = 1180;
const height = 620;
const margin = { top: 54, right: 92, bottom: 76, left: 92 };
const plotWidth = width - margin.left - margin.right;
const plotHeight = height - margin.top - margin.bottom;
const latencyMax = Math.ceil(maxLatency * 1.18 / 50) * 50 || 100;
const vuMax = Math.max(1, Math.ceil(maxVu * 1.12));
function x(second) {
  return margin.left + (second / durationSeconds) * plotWidth;
}
function yLatency(value) {
  return margin.top + plotHeight - (value / latencyMax) * plotHeight;
}
function yVu(value) {
  return margin.top + plotHeight - (value / vuMax) * plotHeight;
}
function polyline(series) {
  return series
    .filter((point) => point.value !== null)
    .map((point) => `${x(point.second).toFixed(2)},${yLatency(point.value).toFixed(2)}`)
    .join(" ");
}
function vuArea(series) {
  const points = series.map((point) => `${x(point.second).toFixed(2)},${yVu(point.value).toFixed(2)}`);
  return [
    `${x(0).toFixed(2)},${(margin.top + plotHeight).toFixed(2)}`,
    ...points,
    `${x(durationSeconds).toFixed(2)},${(margin.top + plotHeight).toFixed(2)}`,
  ].join(" ");
}
function horizontalGrid(axisMax, axisScale, side = "left") {
  const rows = [];
  const step = axisMax <= 100 ? 20 : axisMax <= 500 ? 50 : 100;
  for (let value = 0; value <= axisMax; value += step) {
    const y = axisScale(value);
    rows.push(`<line x1="${margin.left}" y1="${y}" x2="${width - margin.right}" y2="${y}" class="grid" />`);
    const tx = side === "left" ? margin.left - 12 : width - margin.right + 12;
    const anchor = side === "left" ? "end" : "start";
    rows.push(`<text x="${tx}" y="${y + 4}" text-anchor="${anchor}" class="tick">${value}</text>`);
  }
  return rows.join("\n");
}
function verticalGrid() {
  const rows = [];
  const approxTicks = 18;
  const step = Math.max(5, Math.ceil(durationSeconds / approxTicks / 5) * 5);
  for (let second = 0; second <= durationSeconds; second += step) {
    const xx = x(second);
    rows.push(`<line x1="${xx}" y1="${margin.top}" x2="${xx}" y2="${height - margin.bottom}" class="grid vertical" />`);
    rows.push(`<text x="${xx}" y="${height - margin.bottom + 28}" text-anchor="middle" class="tick" transform="rotate(-35 ${xx} ${height - margin.bottom + 28})">${second}</text>`);
  }
  return rows.join("\n");
}
const avgLatency = sortedDurations.reduce((sum, value) => sum + value, 0) / sortedDurations.length;
const reqRate = requestPoints.length / Math.max(1, durationSeconds);
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img" aria-label="${title}">
  <style>
    .title { font: 700 21px Segoe UI, Arial, sans-serif; fill: #111827; }
    .axis-label { font: 600 15px Segoe UI, Arial, sans-serif; fill: #1f6feb; }
    .axis-label.secondary { fill: #737373; }
    .tick { font: 12px Segoe UI, Arial, sans-serif; fill: #44515f; }
    .grid { stroke: #cfd8e3; stroke-width: 1; stroke-dasharray: 2 3; }
    .vertical { stroke-opacity: .7; }
    .axis { stroke: #6b7280; stroke-width: 1.2; }
    .latency { fill: none; stroke: #1f77b4; stroke-width: 2.2; }
    .p95 { stroke: #e3c600; stroke-width: 1.8; stroke-dasharray: 7 4; }
    .p99 { stroke: #ef4444; stroke-width: 1.8; stroke-dasharray: 5 4; }
    .vus { fill: #b8bec7; fill-opacity: .42; stroke: none; }
    .legend-bg { fill: white; stroke: #d1d5db; stroke-width: 1; opacity: .94; }
    .legend { font: 13px Segoe UI, Arial, sans-serif; fill: #1f2937; }
    .meta { font: 12px Segoe UI, Arial, sans-serif; fill: #667085; }
  </style>
  <rect x="0" y="0" width="${width}" height="${height}" rx="8" fill="#ffffff" stroke="#d1d5db" />
  <text x="${width / 2}" y="34" text-anchor="middle" class="title">${title}</text>
  ${horizontalGrid(latencyMax, yLatency, "left")}
  ${verticalGrid()}
  <polygon points="${vuArea(vuSeries)}" class="vus" />
  <line x1="${margin.left}" y1="${yLatency(p95)}" x2="${width - margin.right}" y2="${yLatency(p95)}" class="p95" />
  <line x1="${margin.left}" y1="${yLatency(p99)}" x2="${width - margin.right}" y2="${yLatency(p99)}" class="p99" />
  <polyline points="${polyline(latencySeries)}" class="latency" />
  <line x1="${margin.left}" y1="${height - margin.bottom}" x2="${width - margin.right}" y2="${height - margin.bottom}" class="axis" />
  <line x1="${margin.left}" y1="${margin.top}" x2="${margin.left}" y2="${height - margin.bottom}" class="axis" />
  <line x1="${width - margin.right}" y1="${margin.top}" x2="${width - margin.right}" y2="${height - margin.bottom}" class="axis" />
  ${horizontalGrid(vuMax, yVu, "right")}
  <text x="${width / 2}" y="${height - 20}" text-anchor="middle" class="axis-label secondary">Tempo (s)</text>
  <text x="28" y="${height / 2}" text-anchor="middle" class="axis-label" transform="rotate(-90 28 ${height / 2})">Duracao media das requisicoes (ms)</text>
  <text x="${width - 28}" y="${height / 2}" text-anchor="middle" class="axis-label secondary" transform="rotate(90 ${width - 28} ${height / 2})">Concorrencia (VUs)</text>
  <rect x="${margin.left + 18}" y="${margin.top + 34}" width="300" height="120" rx="6" class="legend-bg" />
  <line x1="${margin.left + 38}" y1="${margin.top + 60}" x2="${margin.left + 72}" y2="${margin.top + 60}" class="latency" />
  <text x="${margin.left + 86}" y="${margin.top + 64}" class="legend">Duracao media das requisicoes (ms)</text>
  <line x1="${margin.left + 38}" y1="${margin.top + 86}" x2="${margin.left + 72}" y2="${margin.top + 86}" class="p95" />
  <text x="${margin.left + 86}" y="${margin.top + 90}" class="legend">P95 (${p95.toFixed(2)} ms)</text>
  <line x1="${margin.left + 38}" y1="${margin.top + 112}" x2="${margin.left + 72}" y2="${margin.top + 112}" class="p99" />
  <text x="${margin.left + 86}" y="${margin.top + 116}" class="legend">P99 (${p99.toFixed(2)} ms)</text>
  <rect x="${margin.left + 38}" y="${margin.top + 132}" width="34" height="16" fill="#b8bec7" opacity=".42" />
  <text x="${margin.left + 86}" y="${margin.top + 145}" class="legend">Concorrencia (VUs)</text>
  <text x="${margin.left}" y="${height - 8}" class="meta">Amostras: ${requestPoints.length} | media global: ${avgLatency.toFixed(2)} ms | vazao aproximada: ${reqRate.toFixed(2)} req/s</text>
</svg>`;
mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, svg, "utf8");
console.log(`Grafico gerado: ${outputPath}`);
