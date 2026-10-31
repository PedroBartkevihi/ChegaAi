import type { Page } from '@playwright/test';
import { test, expect, loginPelaUI } from './fixtures';

// Pedidos do navegador à API a partir daqui, para conferir o que cada passo pede.
function registrarPedidos(page: Page) {
  const pedidos: URL[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (url.pathname.includes('/api/')) pedidos.push(url);
  });
  return pedidos;
}
const rota = (url: URL) => url.pathname.replace(/^.*\/api/, '');
const idsNoFeed = (page: Page) => page.locator('section[aria-label="Feed de eventos"] [data-testid^="event-card-"]')
  .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-testid')!.replace('event-card-', '')));

test('feed traz 12 rolés por vez, sem repetir, e os anteriores só ao abrir a seção', async ({ page, cenario, request }) => {
  const conta = await cenario.criarConta('paginas');
  const criados: string[] = [];
  for (let i = 0; i < 13; i += 1) criados.push((await cenario.criarEvento(conta)).id);
  const pedidos = registrarPedidos(page);
  await loginPelaUI(page, conta);
  const resumo = await (await request.get(cenario.endpoint('/eventos/resumo'))).json();
  const metrica = (nome: string) => page.locator('.metric-row > div').filter({ hasText: nome }).locator('dd');
  await expect(metrica('Rolés abertos')).toHaveText(String(resumo.active));
  await expect(metrica('Na lista de espera')).toHaveText(String(resumo.waitlist));
  await expect(metrica('Rolés de Embaixadores')).toHaveText(String(resumo.ambassadors));

  await page.getByTestId('search-input').fill(cenario.prefixo);
  await expect(page.getByText('Mostrando 12 de 13 rolés', { exact: true })).toBeVisible();
  const primeira = await idsNoFeed(page);
  expect(primeira).toHaveLength(12);
  await page.getByRole('button', { name: 'Carregar mais rolés', exact: true }).click();
  await expect(page.getByText('Mostrando 13 de 13 rolés', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Carregar mais rolés', exact: true })).toHaveCount(0);
  const todos = await idsNoFeed(page);
  expect(todos.slice(0, 12)).toEqual(primeira);
  expect([...todos].sort()).toEqual([...criados].sort());
  // Sem mais páginas o botão some, e o foco segue para o rolé que acabou de chegar.
  await expect(page.getByTestId(`event-card-${todos[12]}`).getByRole('button', { name: 'Detalhes', exact: true })).toBeFocused();
  const paginas = pedidos.filter((url) => rota(url) === '/eventos/feed' && url.searchParams.get('busca') === cenario.prefixo);
  expect(paginas.map((url) => url.searchParams.get('pagina'))).toEqual(['0', '1']);
  expect(pedidos.some((url) => url.searchParams.get('secao') === 'anteriores')).toBe(false);
  expect(pedidos.some((url) => rota(url) === '/eventos')).toBe(false);

  await page.getByRole('button', { name: /Mostrar rolés anteriores/ }).click();
  await expect(page.getByText('Nenhum rolé anterior com os filtros atuais.', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Ocultar rolés anteriores e indisponíveis (0)', exact: true })).toBeVisible();
  const anteriores = pedidos.filter((url) => url.searchParams.get('secao') === 'anteriores');
  expect(anteriores).toHaveLength(1);
  expect(anteriores[0].searchParams.get('busca')).toBe(cenario.prefixo);
});

test('inscrição recarrega só o rolé e os números; voltar à aba repete só a primeira página', async ({ page, cenario }) => {
  const organizador = await cenario.criarConta('organizador');
  const pessoa = await cenario.criarConta('participante');
  const evento = await cenario.criarEvento(organizador);
  await loginPelaUI(page, pessoa);
  const card = page.getByTestId(`event-card-${evento.id}`);
  await expect(card).toContainText('1/3 confirmados');
  const pedidos = registrarPedidos(page);
  await card.getByRole('button', { name: 'Quero participar', exact: true }).click();
  await expect(card.getByRole('button', { name: 'Cancelar presença', exact: true })).toBeEnabled();
  await expect(card).toContainText('2/3 confirmados');
  await expect.poll(() => pedidos.map(rota)).toEqual(expect.arrayContaining(
    [`/eventos/${evento.id}/inscricao`, `/eventos/${evento.id}`, '/eventos/resumo', '/auth/me/inscricoes']));
  expect(pedidos.map(rota)).not.toContain('/eventos/feed');

  pedidos.length = 0;
  const pagina = page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/api/eventos/feed'));
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  const resposta = await pagina;
  expect(Object.fromEntries(new URL(resposta.url()).searchParams)).toEqual({ secao: 'proximos', pagina: '0', tamanho: '12' });
  // Leitura pública sem token nem Content-Type: pedido simples, sem preflight de CORS a cada URL nova.
  expect(resposta.request().headers().authorization).toBeUndefined();
  expect(resposta.request().headers()['content-type']).toBeUndefined();
  await expect.poll(() => pedidos.map(rota)).toContain('/eventos/resumo');
  expect(pedidos.filter((item) => rota(item) === '/eventos/feed')).toHaveLength(1);
});
