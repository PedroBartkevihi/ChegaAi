function metricValue(data, metricName, valueName) {
  const metric = data.metrics[metricName];
  if (!metric || !metric.values || metric.values[valueName] === undefined) {
    return null;
  }
  return metric.values[valueName];
}
function formatNumber(value, digits = 2) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "n/a";
  }
  return Number(value).toFixed(digits);
}
function formatRate(value) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "n/a";
  }
  return `${(Number(value) * 100).toFixed(2)}%`;
}
function row(label, value) {
  return `<tr><th>${label}</th><td>${value}</td></tr>`;
}
function metricRows(data) {
  const avg = metricValue(data, "http_req_duration", "avg");
  const p90 = metricValue(data, "http_req_duration", "p(90)");
  const p95 = metricValue(data, "http_req_duration", "p(95)");
  const max = metricValue(data, "http_req_duration", "max");
  const reqRate = metricValue(data, "http_reqs", "rate");
  const reqCount = metricValue(data, "http_reqs", "count");
  const failedRate = metricValue(data, "http_req_failed", "rate");
  const failedCount = metricValue(data, "http_req_failed", "passes");
  const vusMax = metricValue(data, "vus_max", "max");
  return [
    row("Latencia media", `${formatNumber(avg)} ms`),
    row("p90", `${formatNumber(p90)} ms`),
    row("p95", `${formatNumber(p95)} ms`),
    row("Maior latencia", `${formatNumber(max)} ms`),
    row("Vazao media", `${formatNumber(reqRate)} req/s`),
    row("Total de requisicoes", formatNumber(reqCount, 0)),
    row("Usuarios virtuais maximos", formatNumber(vusMax, 0)),
    row("Taxa de erro", formatRate(failedRate)),
    row("Requisicoes com erro", formatNumber(failedCount, 0)),
  ].join("\n");
}
function thresholds(data) {
  return Object.entries(data.metrics)
    .filter(([, metric]) => metric.thresholds)
    .flatMap(([name, metric]) =>
      Object.entries(metric.thresholds).map(([rule, result]) => ({
        name,
        rule,
        ok: result.ok,
      })),
    );
}
function thresholdRows(data) {
  const items = thresholds(data);
  if (!items.length) {
    return "<tr><td colspan=\"3\">Nenhum threshold configurado.</td></tr>";
  }
  return items
    .map((item) => `<tr><td>${item.name}</td><td>${item.rule}</td><td>${item.ok ? "OK" : "FALHOU"}</td></tr>`)
    .join("\n");
}
export function buildHtmlReport(title, data, details) {
  const generatedAt = new Date().toISOString();
  const stages = details.stages
    .map((stage) => `<li>${stage.duration}: alvo de ${stage.target} VUs</li>`)
    .join("\n");
  return `<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${title}</title>
  <style>
    :root {
      color-scheme: light;
      --bg: #f6f8fb;
      --card: #ffffff;
      --ink: #17212b;
      --muted: #5a6875;
      --line: #d8e0e8;
      --accent: #1f6feb;
    }
    body {
      margin: 0;
      font-family: Inter, Segoe UI, Arial, sans-serif;
      color: var(--ink);
      background: var(--bg);
    }
    main {
      max-width: 1040px;
      margin: 0 auto;
      padding: 32px 20px;
    }
    h1, h2 { margin: 0 0 12px; }
    p { color: var(--muted); line-height: 1.55; }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 16px;
      margin: 20px 0;
    }
    .card {
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 18px;
      background: var(--card);
      box-shadow: 0 8px 24px rgba(31, 53, 79, 0.08);
    }
    table {
      width: 100%;
      border-collapse: collapse;
      background: var(--card);
      border: 1px solid var(--line);
      border-radius: 8px;
      overflow: hidden;
    }
    th, td {
      padding: 12px;
      border-bottom: 1px solid var(--line);
      text-align: left;
    }
    th { width: 260px; background: #f1f5f9; }
    tr:last-child th, tr:last-child td { border-bottom: 0; }
    code {
      padding: 2px 6px;
      border-radius: 6px;
      background: #e9eef5;
    }
    .badge {
      display: inline-block;
      margin-bottom: 12px;
      padding: 4px 10px;
      border-radius: 999px;
      color: #0f3f7a;
      background: #e7f0ff;
      font-size: 0.85rem;
      font-weight: 700;
    }
  </style>
</head>
<body>
  <main>
    <span class="badge">k6 load test</span>
    <h1>${title}</h1>
    <p>Relatorio gerado em <code>${generatedAt}</code>. Use o JSON associado para auditoria completa das metricas brutas.</p>
    <section class="grid">
      <article class="card">
        <h2>Servico</h2>
        <p><strong>${details.service}</strong></p>
        <p>${details.operation} em <code>${details.endpoint}</code></p>
      </article>
      <article class="card">
        <h2>Ambiente</h2>
        <p>Base URL: <code>${details.baseUrl}</code></p>
        <p>Perfil de carga: <code>${details.profile}</code></p>
      </article>
    </section>
    <section class="card">
      <h2>Estagios de carga</h2>
      <ul>${stages}</ul>
    </section>
    <h2>Metricas principais</h2>
    <table>
      <tbody>${metricRows(data)}</tbody>
    </table>
    <h2 style="margin-top: 24px;">Thresholds</h2>
    <table>
      <thead><tr><th>Metrica</th><th>Regra</th><th>Resultado</th></tr></thead>
      <tbody>${thresholdRows(data)}</tbody>
    </table>
  </main>
</body>
</html>`;
}
export function buildTextSummary(title, data, details) {
  const avg = metricValue(data, "http_req_duration", "avg");
  const p95 = metricValue(data, "http_req_duration", "p(95)");
  const reqRate = metricValue(data, "http_reqs", "rate");
  const failedRate = metricValue(data, "http_req_failed", "rate");
  return [
    "",
    `=== ${title} ===`,
    `Servico: ${details.service}`,
    `Endpoint: ${details.endpoint}`,
    `Perfil: ${details.profile}`,
    `Latencia media: ${formatNumber(avg)} ms`,
    `p95: ${formatNumber(p95)} ms`,
    `Vazao media: ${formatNumber(reqRate)} req/s`,
    `Taxa de erro: ${formatRate(failedRate)}`,
    "",
  ].join("\n");
}
export function makeSummary(title, data, details) {
  const html = buildHtmlReport(title, data, details);
  const json = JSON.stringify(data, null, 2);
  return {
    stdout: buildTextSummary(title, data, details),
    [details.htmlReport]: html,
    [details.jsonReport]: json,
  };
}
