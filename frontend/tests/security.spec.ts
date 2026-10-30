import { test, expect, abrirPagina, loginPelaUI } from './fixtures';
import { frontendURL } from './environment';
import { readRenderHeaders } from '../scripts/render-headers.mjs';

const headers = await readRenderHeaders();

async function captureViolations(page) {
  await page.addInitScript(() => {
    const state = window as typeof window & { cspViolations: { directive: string; blocked: string }[] };
    state.cspViolations = [];
    document.addEventListener('securitypolicyviolation', (event) => {
      state.cspViolations.push({ directive: event.effectiveDirective, blocked: event.blockedURI });
    });
  });
}
async function violations(page) {
  return page.evaluate(() => (window as typeof window & { cspViolations: { directive: string; blocked: string }[] }).cspViolations);
}

test.describe('CSP e cabeçalhos do export estático', () => {
  test.skip(process.env.E2E_MODE !== 'static', 'A política de produção é aplicada ao export; next dev mantém as ferramentas de desenvolvimento.');

  test('entrega os cabeçalhos reais do Render e bloqueia script inline e conexão não autorizados', async ({ page, request }) => {
    await captureViolations(page);
    const responsePromise = page.waitForResponse((response) => response.url() === `${frontendURL.origin}/`);
    await abrirPagina(page);
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    for (const [name, value] of Object.entries(headers)) expect(response.headers()[name]).toBe(value);
    for (const [method, path, status] of [['HEAD', '/', 200], ['GET', '/nao-existe', 404], ['POST', '/', 405]] as const) {
      const other = await request.fetch(`${frontendURL.origin}${path}`, { method });
      expect(other.status()).toBe(status);
      for (const [name, value] of Object.entries(headers)) expect(other.headers()[name]).toBe(value);
    }
    expect(await violations(page)).toEqual([]);
    await page.evaluate(() => {
      const probe = document.createElement('script');
      probe.textContent = 'window.cspInlineExecuted = true';
      document.head.append(probe);
    });
    await expect.poll(async () => (await violations(page)).some((item) => item.blocked === 'inline'
      && ['script-src', 'script-src-elem'].includes(item.directive))).toBe(true);
    expect(await page.evaluate(() => (window as typeof window & { cspInlineExecuted?: boolean }).cspInlineExecuted)).toBeUndefined();
    const fetched = await page.evaluate(async () => {
      try { await fetch('data:text/plain,csp-connect-probe'); return true; } catch { return false; }
    });
    expect(fetched).toBe(false);
    await expect.poll(async () => (await violations(page)).some((item) => item.directive === 'connect-src')).toBe(true);
  });

  test('tema salvo é aplicado antes da hidratação, sem violar a CSP', async ({ page }) => {
    await captureViolations(page);
    await page.addInitScript(() => localStorage.setItem('chegaai.theme', 'dark'));
    let release!: () => void;
    let scriptStarted!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    const started = new Promise<void>((resolve) => { scriptStarted = resolve; });
    await page.route(/\/_next\/.*\.js(?:\?.*)?$/, async (route) => {
      scriptStarted();
      await gate;
      await route.continue();
    });
    try {
      await page.goto('/', { waitUntil: 'commit' });
      await started;
      // Os bundles externos ainda estão retidos: só o script inline do head pode ter aplicado o tema.
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
      const ready = page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/api/actuator/health'));
      release();
      await ready;
      await expect(page.getByRole('switch', { name: 'Tema escuro' })).toHaveAttribute('aria-checked', 'true');
      await page.getByRole('switch', { name: 'Tema escuro' }).click();
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
      expect(await violations(page)).toEqual([]);
    } finally { release(); }
  });

  test('mapa em nova aba e arquivo de agenda continuam funcionando sob CSP', async ({ page, context, cenario }) => {
    const pessoa = await cenario.criarConta('mapa-agenda');
    const evento = await cenario.criarEvento(pessoa);
    await captureViolations(page);
    await loginPelaUI(page, pessoa);
    await page.getByTestId(`event-card-${evento.id}`).getByRole('button', { name: 'Detalhes', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: evento.titulo, exact: true });
    const map = dialog.getByRole('link', { name: /Ver no mapa/ });
    const href = await map.getAttribute('href');
    expect(href).toBeTruthy();
    const destination = new URL(href!);
    expect(destination.origin).toBe('https://www.google.com');
    expect(destination.pathname).toBe('/maps/search/');
    await expect(map).toHaveAttribute('target', '_blank');
    await expect(map).toHaveAttribute('rel', 'noopener noreferrer');
    // Navegação fictícia, interceptada antes da rede; nenhum dado é enviado ao Google.
    await context.route(href!, async (route) => {
      expect(route.request().isNavigationRequest()).toBe(true);
      await route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><title>Mapa fictício do teste</title>' });
    });
    const popupPromise = page.waitForEvent('popup');
    await map.click();
    const popup = await popupPromise;
    await popup.waitForLoadState();
    expect(popup.url()).toBe(href);
    await popup.close();
    const downloadPromise = page.waitForEvent('download');
    await dialog.getByRole('button', { name: 'Adicionar à agenda', exact: true }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.ics$/);
    const stream = await download.createReadStream();
    if (!stream) throw new Error('O download da agenda não gerou um arquivo.');
    const chunks: Buffer[] = [];
    for await (const chunk of stream) chunks.push(chunk);
    const content = Buffer.concat(chunks).toString('utf8');
    expect(content).toContain('BEGIN:VCALENDAR\r\n');
    expect(content).toContain(`UID:${evento.id}@chegaai\r\n`);
    expect(content).toContain(`SUMMARY:${evento.titulo}\r\n`);
    expect(await violations(page)).toEqual([]);
  });
});
