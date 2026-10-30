import { test, expect, abrirPagina } from './fixtures';

test('429 no login preserva os campos e permite nova tentativa', async ({ page, cenario }) => {
  const conta = await cenario.criarConta('loginLimitado');
  let tentativas = 0;
  const mensagem = 'Muitas tentativas em pouco tempo. Aguarde 8 segundos e tente novamente.';
  await page.route('**/api/auth/login', async (route) => {
    tentativas++;
    if (tentativas === 1) return route.fulfill({ status: 429, contentType: 'application/json',
      headers: { 'Retry-After': '8', 'Cache-Control': 'no-store',
        'Access-Control-Allow-Origin': new URL(page.url()).origin, 'Access-Control-Expose-Headers': 'Retry-After' },
      body: JSON.stringify({ status: 429, error: 'Too Many Requests', message: mensagem }) });
    return route.continue();
  });
  await abrirPagina(page);
  await page.getByTestId('input-email').fill(conta.email);
  await page.getByTestId('input-password').fill(conta.senha);
  const recusada = page.waitForResponse((response) => response.url().endsWith('/api/auth/login') && response.request().method() === 'POST');
  await page.getByTestId('btn-login').click();
  expect((await recusada).status()).toBe(429);
  await expect(page.locator('.login-error')).toContainText(mensagem);
  await expect(page.getByTestId('input-email')).toHaveValue(conta.email);
  await expect(page.getByTestId('input-password')).toHaveValue(conta.senha);
  await expect(page.getByTestId('btn-login')).toBeEnabled();
  expect(tentativas).toBe(1);
  expect(await page.evaluate(() => localStorage.getItem('chegaai.token'))).toBeNull();
  const aceita = page.waitForResponse((response) => response.url().endsWith('/api/auth/login') && response.request().method() === 'POST');
  await page.getByTestId('btn-login').click();
  const resposta = await aceita;
  expect(resposta.status()).toBe(200);
  const auth = await resposta.json();
  expect(auth.user.id).toBe(conta.id);
  await expect(page.locator('section[aria-label="Feed de eventos"]')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('chegaai.token'))).toBe(auth.token);
  expect(tentativas).toBe(2);
});
