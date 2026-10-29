import { test, expect, loginPelaUI } from './fixtures';

test('429 de comentário mantém a sessão e o texto para tentar novamente', async ({ page, cenario }) => {
  const pessoa = await cenario.criarConta('limiteComentario');
  const evento = await cenario.criarEvento(pessoa);
  await loginPelaUI(page, pessoa);
  const token = await page.evaluate(() => localStorage.getItem('chegaai.token'));
  await page.getByTestId(`event-card-${evento.id}`).getByRole('button', { name: 'Detalhes', exact: true }).click();
  const dialogo = page.getByRole('dialog', { name: evento.titulo, exact: true });
  const texto = cenario.unico('comentario-repetido');
  const endpoint = `**/api/eventos/${evento.id}/comentarios`;
  await page.route(endpoint, async (route) => {
    await route.fulfill({ status: 429, contentType: 'application/json', headers: { 'Retry-After': '60', 'Access-Control-Allow-Origin': '*' },
      body: JSON.stringify({ status: 429, error: 'Too Many Requests', message: 'Você atingiu o limite de comentários. Aguarde e tente novamente.' }) });
  });
  await dialogo.getByRole('textbox', { name: 'Novo comentário', exact: true }).fill(texto);
  await dialogo.getByRole('button', { name: 'Enviar comentário', exact: true }).click();
  await expect(dialogo.getByText('Você atingiu o limite de comentários. Aguarde e tente novamente.', { exact: true })).toBeVisible();
  await expect(dialogo.getByRole('textbox', { name: 'Novo comentário', exact: true })).toHaveValue(texto);
  await expect(dialogo.getByRole('button', { name: 'Enviar comentário', exact: true })).not.toHaveAttribute('aria-disabled', 'true');
  expect(await page.evaluate(() => localStorage.getItem('chegaai.token'))).toBe(token);
  expect((await cenario.obterEvento(evento)).comments.some((comentario) => comentario.text === texto)).toBe(false);
  await page.unroute(endpoint);
  await dialogo.getByRole('button', { name: 'Enviar comentário', exact: true }).click();
  await expect(dialogo.getByText(texto, { exact: true })).toBeVisible();
  await expect(dialogo.getByRole('textbox', { name: 'Novo comentário', exact: true })).toHaveValue('');
  expect((await cenario.obterEvento(evento)).comments.filter((comentario) => comentario.text === texto)).toHaveLength(1);
  expect(await page.evaluate(() => localStorage.getItem('chegaai.token'))).toBe(token);
});
