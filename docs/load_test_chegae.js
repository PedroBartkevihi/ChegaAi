/**
 * ChegAê — Teste de Carga com k6
 *
 * Serviços testados:
 *   S1 — GET  /api/eventos          (leitura — listar todos os eventos)
 *   S2 — POST /api/eventos/:id/inscricao  (escrita — toggle de inscrição com fila FIFO)
 *
 * Como executar:
 *   1. Instale k6: https://k6.io/docs/getting-started/installation/
 *   2. Suba o backend: ./mvnw spring-boot:run  (porta 8080)
 *   3. Suba o PostgreSQL: docker compose -f infra/docker-compose.yml up -d postgres
 *   4. Rode o teste:
 *        k6 run load_test_chegae.js
 *      Para exportar CSV (para gráficos):
 *        k6 run --out csv=results.csv load_test_chegae.js
 *      Para exportar JSON (para análise):
 *        k6 run --out json=results.json load_test_chegae.js
 *
 * Fases do teste (rampa de carga):
 *   0-30s   →  1  VU   (warm-up — linha de base)
 *   30-60s  →  10 VUs  (carga leve)
 *   60-120s →  50 VUs  (carga média)
 *   120-180s → 100 VUs (carga alta)
 *   180-210s → 200 VUs (pico / estresse)
 *   210-240s →  0 VUs  (cool-down)
 *
 * Métricas coletadas:
 *   - http_req_duration  → latência (p50, p90, p95, p99, média)
 *   - http_reqs          → vazão (requisições/s)
 *   - http_req_failed    → taxa de erro
 *   - vus                → concorrência atual
 *   - iterations         → total de ciclos executados
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================================
// Configuração de ambiente
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

// IDs fixos injetados pelo DataSeederService.
// Se o banco foi resetado, rode: k6 run --env DISCOVER_IDS=true load_test_chegae.js
// para que o script descubra os IDs dinamicamente antes de testar.
const DISCOVER_IDS = __ENV.DISCOVER_IDS === 'true';

// Usuários do seeder com honra >= 3.0 (podem se inscrever — RN004)
const USUARIOS = [
    { email: 'pedro@chegae.dev', nome: 'Pedro' },
    { email: 'caio@chegae.dev', nome: 'Caio' },
    { email: 'marcos@chegae.dev', nome: 'Marcos' },
    { email: 'lia@chegae.dev', nome: 'Lia' },
    { email: 'rafael@chegae.dev', nome: 'Rafael' },
    { email: 'bia@chegae.dev', nome: 'Bia' },
    { email: 'paula@chegae.dev', nome: 'Paula' },
    { email: 'bruno@chegae.dev', nome: 'Bruno' },
    { email: 'nina@chegae.dev', nome: 'Nina' },
];

// ============================================================
// Métricas customizadas
// ============================================================

const erroS1 = new Rate('s1_erros');
const erroS2 = new Rate('s2_erros');
const latenciaS1 = new Trend('s1_latencia_ms', true);
const latenciaS2 = new Trend('s2_latencia_ms', true);
const totalS1 = new Counter('s1_total_requisicoes');
const totalS2 = new Counter('s2_total_requisicoes');

// ============================================================
// Configuração das fases (rampa de carga)
// ============================================================

export const options = {
    stages: [
        { duration: '30s', target: 1 }, // warm-up
        { duration: '30s', target: 10 }, // carga leve
        { duration: '60s', target: 50 }, // carga média
        { duration: '60s', target: 100 }, // carga alta
        { duration: '30s', target: 200 }, // pico / estresse
        { duration: '30s', target: 0 }, // cool-down
    ],

    thresholds: {
        // S1 — Listagem de eventos: p95 < 500ms, erro < 1%
        's1_latencia_ms': ['p(95)<500'],
        's1_erros': ['rate<0.01'],

        // S2 — Toggle inscrição: p95 < 1000ms, erro < 2%
        's2_latencia_ms': ['p(95)<1000'],
        's2_erros': ['rate<0.02'],

        // Geral
        'http_req_failed': ['rate<0.05'],
    },

    summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ============================================================
// Setup: descobre IDs e faz login dos usuários
// ============================================================

export function setup() {
    // 1. Login de todos os usuários para obter seus IDs
    const sessoes = {};
    for (const u of USUARIOS) {
        const res = http.post(
            `${BASE_URL}/auth/login`,
            JSON.stringify({ email: u.email }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (res.status === 200) {
            const body = JSON.parse(res.body);
            sessoes[u.email] = body.id;
        } else {
            console.warn(`Login falhou para ${u.email}: ${res.status}`);
        }
    }

    // 2. Buscar lista de eventos para obter IDs reais
    const evtRes = http.get(`${BASE_URL}/eventos`);
    let eventoIds = [];
    if (evtRes.status === 200) {
        const eventos = JSON.parse(evtRes.body);
        // Pegar apenas eventos ABERTO (não travados, não cancelados)
        eventoIds = eventos
            .filter(e => e.status === 'ABERTO')
            .map(e => e.id);
        console.log(`Eventos ABERTO encontrados: ${eventoIds.length}`);
    } else {
        console.error(`Falha ao buscar eventos: ${evtRes.status}`);
    }

    return { sessoes, eventoIds };
}

// ============================================================
// Cenário principal
// ============================================================

export default function (data) {
    const { sessoes, eventoIds } = data;
    const emails = Object.keys(sessoes);

    if (emails.length === 0 || eventoIds.length === 0) {
        console.error('Setup incompleto: sem usuários ou eventos.');
        return;
    }

    // Selecionar usuário e evento aleatórios para esta iteração
    const emailAleatorio = emails[Math.floor(Math.random() * emails.length)];
    const usuarioId = sessoes[emailAleatorio];
    const eventoId = eventoIds[Math.floor(Math.random() * eventoIds.length)];

    const headersComAuth = {
        'Content-Type': 'application/json',
        'X-User-Id': usuarioId,
    };

    // ──────────────────────────────────────────────
    // S1: Listagem de eventos (leitura)
    // Endpoint: GET /api/eventos
    // ──────────────────────────────────────────────
    group('S1 — GET /eventos (leitura)', () => {
        const inicio = Date.now();

        const res = http.get(`${BASE_URL}/eventos`, {
            headers: headersComAuth,
            tags: { servico: 'S1_listar_eventos' },
        });

        const duracao = Date.now() - inicio;
        latenciaS1.add(duracao);
        totalS1.add(1);

        const ok = check(res, {
            'S1: status 200': (r) => r.status === 200,
            'S1: body é array': (r) => {
                try { return Array.isArray(JSON.parse(r.body)); }
                catch { return false; }
            },
            'S1: latência < 1000ms': () => duracao < 1000,
        });

        erroS1.add(!ok);
    });

    sleep(0.2); // pequena pausa entre os dois grupos

    // ──────────────────────────────────────────────
    // S2: Toggle de inscrição (escrita)
    // Endpoint: POST /api/eventos/:id/inscricao
    // Implementa RN001 (fila FIFO) + RN004 (honra) + RNF003 (concorrência)
    // ──────────────────────────────────────────────
    group('S2 — POST /inscricao (escrita com FIFO)', () => {
        const inicio = Date.now();

        const res = http.post(
            `${BASE_URL}/eventos/${eventoId}/inscricao`,
            null, // body vazio; autenticação via header
            {
                headers: headersComAuth,
                tags: { servico: 'S2_toggle_inscricao' },
            }
        );

        const duracao = Date.now() - inicio;
        latenciaS2.add(duracao);
        totalS2.add(1);

        // Respostas válidas:
        // 200 = inscrito/cancelado com sucesso
        // 409 = conflito de negócio (evento travado, honra baixa, etc.) — esperado sob carga
        const statusValido = res.status === 200 || res.status === 409;

        const ok = check(res, {
            'S2: status válido (200 ou 409)': () => statusValido,
            'S2: body tem campo message': (r) => {
                try {
                    const b = JSON.parse(r.body);
                    return b.message !== undefined || b.status !== undefined;
                } catch { return false; }
            },
            'S2: latência < 2000ms': () => duracao < 2000,
        });

        // Só conta como erro se for 5xx (falha real, não conflito de negócio)
        erroS2.add(res.status >= 500);
    });

    // Think time: simula usuário real entre requisições
    sleep(Math.random() * 0.8 + 0.2); // 0.2s a 1.0s
}

// ============================================================
// Teardown: imprime resumo no console
// ============================================================

export function teardown(data) {
    console.log('='.repeat(60));
    console.log('Teste de carga ChegAê finalizado.');
    console.log(`Usuários carregados: ${Object.keys(data.sessoes).length}`);
    console.log(`Eventos testados:    ${data.eventoIds.length}`);
    console.log('='.repeat(60));
}