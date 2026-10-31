// Relatório de uma medição do tests/load/medir.sh, só com os dados da execução (nada digitado à mão).
//   node tests/load/tools/relatorio.mjs medicao <pasta-do-resultado> <pasta-de-destino>
//     escreve no destino: metadados.json, resumo.json (as métricas do k6), por-segundo.csv, latencia.svg e medicao.md
//   node tests/load/tools/relatorio.mjs comparar <arquivo.md> <destino1> <destino2> ...
//     escreve uma tabela que compara medições já geradas
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { basename, join, relative } from "node:path";
import { pathToFileURL } from "node:url";

// Percentil com interpolação linear entre as amostras vizinhas.
export function percentil(ordenados, p) {
  if (!ordenados.length) return null;
  const posicao = (p / 100) * (ordenados.length - 1);
  const abaixo = Math.floor(posicao);
  const acima = Math.ceil(posicao);
  return ordenados[abaixo] + (ordenados[acima] - ordenados[abaixo]) * (posicao - abaixo);
}

// Pontos da saída --out json do k6. Pedidos do setup e do teardown ficam de fora.
export function lerSerie(texto) {
  const duracoes = [];
  const vus = [];
  for (const linha of texto.split(/\r?\n/)) {
    if (!linha.trim()) continue;
    let registro;
    try {
      registro = JSON.parse(linha);
    } catch {
      continue;
    }
    if (registro.type !== "Point") continue;
    const tempo = Date.parse(registro.data?.time);
    const valor = Number(registro.data?.value);
    if (!Number.isFinite(tempo) || !Number.isFinite(valor)) continue;
    const tags = registro.data?.tags || {};
    if (registro.metric === "http_req_duration" && !tags.phase) duracoes.push({ tempo, valor, nome: tags.name || "" });
    else if (registro.metric === "vus") vus.push({ tempo, valor });
  }
  return { duracoes, vus };
}

export function porSegundo({ duracoes, vus }) {
  if (!duracoes.length) return [];
  // Sem espalhar os pontos num Math.min: uma medição longa passa do limite de argumentos.
  const inicio = [...duracoes, ...vus].reduce((menor, ponto) => Math.min(menor, ponto.tempo), Infinity);
  const segundos = new Map();
  for (const ponto of duracoes) {
    const segundo = Math.floor((ponto.tempo - inicio) / 1000);
    if (!segundos.has(segundo)) segundos.set(segundo, []);
    segundos.get(segundo).push(ponto.valor);
  }
  const vusPorSegundo = new Map();
  for (const ponto of vus) vusPorSegundo.set(Math.floor((ponto.tempo - inicio) / 1000), ponto.valor);
  const ultimo = Math.max(...segundos.keys(), ...vusPorSegundo.keys());
  const linhas = [];
  let vusAtuais = 0;
  for (let segundo = 0; segundo <= ultimo; segundo += 1) {
    if (vusPorSegundo.has(segundo)) vusAtuais = vusPorSegundo.get(segundo);
    const valores = (segundos.get(segundo) || []).sort((a, b) => a - b);
    linhas.push({
      segundo,
      vus: vusAtuais,
      requisicoes: valores.length,
      media: valores.length ? valores.reduce((soma, valor) => soma + valor, 0) / valores.length : null,
      p95: percentil(valores, 95),
    });
  }
  return linhas;
}

// Do resumo do handleSummary: o total da carga e cada rota que tem limite próprio (http_req_duration{name:...}).
// O total vem da marca do cenário (operation), que deixa de fora o setup e o teardown; sem ela, do total geral.
export function metricasDoResumo(resumo) {
  const metricas = resumo.metrics || {};
  const total = Object.keys(metricas).find((nome) => nome.startsWith("http_req_duration{operation:")) || "http_req_duration";
  const linha = (rota, metrica) => ({
    rota,
    contagem: metrica.values.count ?? null,
    media: metrica.values.avg,
    p90: metrica.values["p(90)"],
    p95: metrica.values["p(95)"],
    maximo: metrica.values.max,
  });
  const porRota = Object.entries(metricas)
    .filter(([nome]) => nome.startsWith("http_req_duration{name:"))
    .map(([nome, metrica]) => linha(nome.slice("http_req_duration{name:".length, -1), metrica))
    .sort((a, b) => (b.contagem ?? 0) - (a.contagem ?? 0));
  const rotas = metricas[total] ? [linha("Todas", metricas[total]), ...porRota] : porRota;
  const limites = Object.entries(metricas).flatMap(([nome, metrica]) =>
    Object.entries(metrica.thresholds || {}).map(([regra, resultado]) => ({ metrica: nome, regra, ok: resultado.ok })));
  // Requisições e vazão também só da carga, como a linha "Todas".
  const duracao = resumo.state?.testRunDurationMs ? resumo.state.testRunDurationMs / 1000 : null;
  const carga = metricas[total]?.values?.count ?? null;
  return {
    rotas,
    vazao: carga !== null && duracao ? carga / duracao : metricas.http_reqs?.values?.rate ?? null,
    requisicoes: carga ?? metricas.http_reqs?.values?.count ?? null,
    erros: metricas.http_req_failed?.values?.rate ?? null,
    vusMax: metricas.vus_max?.values?.max ?? null,
    limites,
  };
}

const ms = (valor) => (valor === null || valor === undefined ? "–" : valor.toFixed(1).replace(".", ","));
const pct = (valor) => (valor === null || valor === undefined ? "–" : `${(valor * 100).toFixed(2).replace(".", ",")} %`);

// Marcas redondas (1, 2 ou 5 vezes uma potência de 10) que cobrem o máximo em cerca de 4 passos.
export function escala(maximo, inteira = false) {
  const bruto = Math.max(maximo, 1) / 4;
  const potencia = 10 ** Math.floor(Math.log10(bruto));
  let passo = [1, 2, 5, 10].map((fator) => fator * potencia).find((valor) => valor >= bruto);
  if (inteira) passo = Math.max(1, Math.round(passo));
  const marcas = [];
  for (let valor = 0; valor < Math.max(maximo, 1) + passo; valor += passo) marcas.push(Number(valor.toFixed(6)));
  return marcas;
}

export function graficoSvg(linhas, titulo) {
  const largura = 960;
  const altura = 420;
  const m = { cima: 48, direita: 64, baixo: 60, esquerda: 64 };
  const w = largura - m.esquerda - m.direita;
  const h = altura - m.cima - m.baixo;
  const duracao = Math.max(1, linhas.length - 1);
  const marcasMs = escala(Math.max(...linhas.map((linha) => linha.p95 || 0)));
  const marcasVus = escala(Math.max(...linhas.map((linha) => linha.vus)), true);
  const topoMs = marcasMs.at(-1);
  const topoVus = marcasVus.at(-1);
  const x = (segundo) => (m.esquerda + (segundo / duracao) * w).toFixed(1);
  const yMs = (valor) => (m.cima + h - (valor / topoMs) * h).toFixed(1);
  const yVus = (valor) => (m.cima + h - (valor / topoVus) * h).toFixed(1);
  const linha = (campo) => linhas.filter((l) => l[campo] !== null).map((l) => `${x(l.segundo)},${yMs(l[campo])}`).join(" ");
  const area = [`${x(0)},${m.cima + h}`, ...linhas.map((l) => `${x(l.segundo)},${yVus(l.vus)}`), `${x(duracao)},${m.cima + h}`].join(" ");
  const marcas = marcasMs.map((valor) => `<line x1="${m.esquerda}" x2="${largura - m.direita}" y1="${yMs(valor)}" y2="${yMs(valor)}" class="grade"/>`
    + `<text x="${m.esquerda - 8}" y="${Number(yMs(valor)) + 4}" text-anchor="end" class="marca">${valor}</text>`).join("")
    + marcasVus.map((valor) => `<text x="${largura - m.direita + 8}" y="${Number(yVus(valor)) + 4}" class="marca">${valor}</text>`).join("")
    + escala(duracao, true).filter((segundo) => segundo <= duracao)
      .map((segundo) => `<text x="${x(segundo)}" y="${m.cima + h + 18}" text-anchor="middle" class="marca">${segundo}</text>`).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${largura}" height="${altura}" viewBox="0 0 ${largura} ${altura}" role="img" aria-label="${titulo}">
<style>.titulo{font:600 16px sans-serif;fill:#111827}.marca{font:11px sans-serif;fill:#4b5563}.eixo{font:12px sans-serif;fill:#374151}.grade{stroke:#e5e7eb}.media{fill:none;stroke:#2563eb;stroke-width:2}.p95{fill:none;stroke:#dc2626;stroke-width:1.5;stroke-dasharray:5 3}.vus{fill:#9ca3af;fill-opacity:.25}</style>
<rect width="${largura}" height="${altura}" fill="#ffffff"/>
<text x="${largura / 2}" y="28" text-anchor="middle" class="titulo">${titulo}</text>
${marcas}
<polygon points="${area}" class="vus"/>
<polyline points="${linha("media")}" class="media"/>
<polyline points="${linha("p95")}" class="p95"/>
<text x="${largura / 2}" y="${altura - 14}" text-anchor="middle" class="eixo">segundos · azul: média (ms, eixo esquerdo) · vermelho tracejado: p95 (ms) · cinza: usuários virtuais (eixo direito)</text>
</svg>
`;
}

function descricao(metadados) {
  const nomes = { "eventos-feed": "Leitura do feed", "comentarios-write": "Escrita de comentários" };
  const roteiro = metadados.script === "eventos-feed" ? (metadados.roteiro === "lista" ? ", lista completa antiga" : ", feed paginado") : "";
  return `${nomes[metadados.script] || metadados.script}${roteiro}, base ${metadados.base.nome}, código ${metadados.versaoApi}`;
}

export function textoMedicao(metadados, metricas) {
  const { base, ambiente, memoriaMiB: memoria } = metadados;
  const falhas = metricas.limites.filter((limite) => !limite.ok);
  return [
    `# ${descricao(metadados)}`,
    "",
    "| | |",
    "|---|---|",
    `| Data | ${metadados.data} |`,
    `| Código medido | \`${metadados.versaoApi}\` (scripts em \`${metadados.versaoScripts}\`) |`,
    `| Base | ${base.eventos} rolés, ${base.inscricoes} inscrições, ${base.comentarios} comentários |`,
    `| Carga | perfil \`${metadados.perfil}\`, até ${metricas.vusMax ?? "–"} usuários virtuais |`,
    `| API | ${ambiente.api} |`,
    `| Banco e k6 | ${ambiente.banco}; ${ambiente.k6} |`,
    `| Máquina | ${ambiente.cpuDoHost}; Docker com ${ambiente.docker} |`,
    `| Memória da API | pico de ${memoria.pico === null ? "–" : `${Math.round(memoria.pico)} MiB`} de 512 MiB (${memoria.amostras} amostras)${memoria.oom ? ", **com OOM**" : ", sem OOM"} |`,
    "",
    "| Rota | Requisições | Média (ms) | p90 (ms) | p95 (ms) | Máximo (ms) |",
    "|---|---:|---:|---:|---:|---:|",
    ...metricas.rotas.map((r) => `| ${r.rota} | ${r.contagem ?? "–"} | ${ms(r.media)} | ${ms(r.p90)} | ${ms(r.p95)} | ${ms(r.maximo)} |`),
    "",
    `Vazão média: ${metricas.vazao === null ? "–" : metricas.vazao.toFixed(1).replace(".", ",")} req/s. Erros: ${pct(metricas.erros)}.`
      + ` Limites do SLA: ${falhas.length ? `falharam ${falhas.map((f) => `\`${f.metrica}\` ${f.regra}`).join(", ")}` : "todos dentro"}.`,
    "",
    "![Latência e usuários virtuais por segundo](latencia.svg)",
    "",
  ].join("\n");
}

function gerarMedicao(origem, destino) {
  const metadados = JSON.parse(readFileSync(join(origem, "metadados.json"), "utf8"));
  const arquivoResumo = readdirSync(origem).find((nome) => nome.endsWith("-summary.json"));
  if (!arquivoResumo) throw new Error(`Sem o resumo do k6 (*-summary.json) em ${origem}`);
  const resumo = JSON.parse(readFileSync(join(origem, arquivoResumo), "utf8"));
  const linhas = porSegundo(lerSerie(readFileSync(join(origem, "serie.jsonl"), "utf8")));
  const metricas = metricasDoResumo(resumo);
  mkdirSync(destino, { recursive: true });
  copyFileSync(join(origem, "metadados.json"), join(destino, "metadados.json"));
  writeFileSync(join(destino, "resumo.json"), `${JSON.stringify({ state: { testRunDurationMs: resumo.state?.testRunDurationMs },
    metrics: resumo.metrics }, null, 2)}\n`);
  writeFileSync(join(destino, "por-segundo.csv"), ["segundo,vus,requisicoes,media_ms,p95_ms",
    ...linhas.map((l) => [l.segundo, l.vus, l.requisicoes, l.media?.toFixed(2) ?? "", l.p95?.toFixed(2) ?? ""].join(","))].join("\n") + "\n");
  writeFileSync(join(destino, "latencia.svg"), graficoSvg(linhas, descricao(metadados)));
  writeFileSync(join(destino, "medicao.md"), textoMedicao(metadados, metricas));
  console.log(`Relatório da medição em ${destino}`);
}

export function textoComparacao(medicoes, arquivo) {
  return [
    "| Medição | Requisições | Vazão (req/s) | Média (ms) | p95 (ms) | Erros | Pico de memória |",
    "|---|---:|---:|---:|---:|---:|---:|",
    ...medicoes.map(({ pasta, metadados, metricas }) => {
      const todas = metricas.rotas.find((r) => r.rota === "Todas") || {};
      const link = relative(join(arquivo, ".."), join(pasta, "medicao.md")).replaceAll("\\", "/");
      return `| [${descricao(metadados)}](${link}) | ${metricas.requisicoes ?? "–"} | ${metricas.vazao === null ? "–" : metricas.vazao.toFixed(1).replace(".", ",")}`
        + ` | ${ms(todas.media)} | ${ms(todas.p95)} | ${pct(metricas.erros)} | ${metadados.memoriaMiB.pico === null ? "–" : `${Math.round(metadados.memoriaMiB.pico)} MiB`} |`;
    }),
    "",
  ].join("\n");
}

function gerarComparacao(arquivo, pastas) {
  const medicoes = pastas.map((pasta) => ({
    pasta,
    metadados: JSON.parse(readFileSync(join(pasta, "metadados.json"), "utf8")),
    metricas: metricasDoResumo(JSON.parse(readFileSync(join(pasta, "resumo.json"), "utf8"))),
  }));
  writeFileSync(arquivo, textoComparacao(medicoes, arquivo));
  console.log(`Comparação de ${medicoes.length} medições em ${arquivo}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [modo, ...args] = process.argv.slice(2);
  if (modo === "medicao" && args.length === 2 && existsSync(args[0])) gerarMedicao(args[0], args[1]);
  else if (modo === "comparar" && args.length >= 2) gerarComparacao(args[0], args.slice(1));
  else {
    console.error(`Uso:\n  node tests/load/tools/${basename(process.argv[1])} medicao <pasta-do-resultado> <pasta-de-destino>\n`
      + `  node tests/load/tools/${basename(process.argv[1])} comparar <arquivo.md> <destino1> <destino2> ...`);
    process.exit(2);
  }
}
