import { test, expect, abrirPagina, loginPelaUI, type EventoDTO } from './fixtures';
import { apiURL } from './environment';

test.describe('ChegaAí — fluxos com dados próprios', () => {
  test('CT01 — login válido carrega sessão e rolé criado pela fixture', async ({ page, cenario }) => {
    const conta = await cenario.criarConta('login');
    const evento = await cenario.criarEvento(conta);
    const user = await loginPelaUI(page, conta);
    expect(user.email).toBe(conta.email);
    await expect(page.getByTestId(`event-card-${evento.id}`)).toContainText(evento.titulo);
    await page.reload();
    await expect(page.getByTestId(`event-card-${evento.id}`)).toBeVisible();
  });

  test('CT02 — publicação pela UI persiste o rolé e seu organizador', async ({ page, cenario }) => {
    const conta = await cenario.criarConta('organizador');
    const dados = cenario.dadosEvento({ capacity: 10 });
    await loginPelaUI(page, conta);
    await page.locator('[aria-label="Navegação principal"]').getByRole('button', { name: 'Criar rolé', exact: true }).click();
    const form = page.locator('section[aria-label="Criar evento"]');
    await form.locator('input[name="title"]').fill(dados.title);
    await form.locator('select[name="category"]').selectOption(dados.category);
    await form.locator('input[name="date"]').fill(dados.date);
    await form.locator('input[name="time"]').fill(dados.time);
    await form.locator('input[name="place"]').fill(dados.place);
    // A distância saiu: a API a ignorava e devolvia sempre 0.
    await expect(form.locator('input[name="distance"]')).toHaveCount(0);
    await form.locator('textarea[name="description"]').fill(dados.description);
    await form.getByTestId('input-capacidade').fill(String(dados.capacity));
    const resposta = page.waitForResponse((r) => r.url().endsWith('/api/eventos') && r.request().method() === 'POST');
    await form.getByRole('button', { name: /publicar rolé/i }).click();
    const response = await resposta;
    expect(response.status()).toBe(201);
    const dto: EventoDTO = await response.json();
    const evento = cenario.rastrearEventoDTO(dto, conta);
    expect(dto).toMatchObject({ title: dados.title, category: dados.category, capacity: 10, organizer: { id: conta.id } });
    expect((await cenario.obterEvento(evento)).title).toBe(dados.title);
    await expect(page.getByTestId(`event-card-${evento.id}`)).toContainText(dados.title);
    await page.reload();
    await expect(page.getByTestId(`event-card-${evento.id}`)).toBeVisible();
  });

  test('CT03 — honra baixa bloqueia a criação sem alterar a conta demo', async ({ page, cenario }) => {
    // Única demo usada: cenário de honra inicial explícita, somente leitura.
    const user = await loginPelaUI(page, { email: 'baixa@chegaai.dev', senha: 'admin123' });
    expect(user.honor).toBeLessThan(2);
    const idsAntes = await cenario.idsDeTodosOsRoles();
    await page.locator('[aria-label="Navegação principal"]').getByRole('button', { name: 'Criar rolé', exact: true }).click();
    await expect(page.locator('.notice.alert-error')).toContainText(/reputação|honra/i);
    await expect(page.locator('section[aria-label="Criar evento"]')).toHaveCount(0);
    expect(await cenario.idsDeTodosOsRoles()).toEqual(idsAntes);
  });

  test('CT04 — participante envia comentário próprio e o reencontra após recarregar', async ({ page, cenario }) => {
    const organizador = await cenario.criarConta('organizador');
    const participante = await cenario.criarConta('comentador');
    const evento = await cenario.criarEvento(organizador);
    expect((await cenario.inscrever(evento, participante)).status).toBe('CONFIRMADO');
    const texto = cenario.unico('comentario');
    const comentariosAntes = (await cenario.obterEvento(evento)).comments;
    await loginPelaUI(page, participante);
    await page.getByTestId(`event-card-${evento.id}`).getByRole('button', { name: /detalhes/i }).click();
    const modal = page.getByRole('dialog', { name: evento.titulo });
    await modal.locator('form.comment-form textarea').fill(texto);
    const resposta = page.waitForResponse((r) => r.url().endsWith(`/api/eventos/${evento.id}/comentarios`) && r.request().method() === 'POST');
    await modal.getByRole('button', { name: 'Enviar comentário', exact: true }).click();
    const response = await resposta;
    expect(response.status()).toBe(201);
    expect(await response.json()).toMatchObject({ author: participante.nome, text: texto });
    await expect(modal.getByText(texto, { exact: true })).toHaveCount(1);
    expect((await cenario.obterEvento(evento)).comments).toEqual([...comentariosAntes, expect.objectContaining({ author: participante.nome, text: texto })]);
    await page.reload();
    await page.getByTestId(`event-card-${evento.id}`).getByRole('button', { name: /detalhes/i }).click();
    await expect(page.getByRole('dialog', { name: evento.titulo }).getByText(texto, { exact: true })).toBeVisible();
  });

  test('CT05 — busca e categoria retornam exatamente os rolés esperados', async ({ page, cenario }) => {
    const conta = await cenario.criarConta('filtros');
    const a = await cenario.criarEvento(conta, { title: `${cenario.prefixo} Futebol A`, category: 'Futebol' });
    const b = await cenario.criarEvento(conta, { title: `${cenario.prefixo} Futebol B`, category: 'Futebol' });
    const c = await cenario.criarEvento(conta, { title: `${cenario.prefixo} Pets C`, category: 'Pets' });
    await loginPelaUI(page, conta);
    const cards = page.locator('section[aria-label="Feed de eventos"] [data-testid^="event-card-"]');
    const ids = () => cards.evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-testid')!.replace('event-card-', '')).sort());
    await page.getByTestId('search-input').fill(cenario.prefixo);
    await expect.poll(ids).toEqual([a.id, b.id, c.id].sort());
    await page.getByRole('combobox', { name: /^Categoria/ }).selectOption('Futebol');
    await expect.poll(ids).toEqual([a.id, b.id].sort());
    await page.getByTestId('search-input').fill(a.titulo);
    await expect.poll(ids).toEqual([a.id]);
    await page.getByTestId('search-input').fill(cenario.unico('inexistente'));
    await expect(cards).toHaveCount(0);
    await expect(page.getByText('Nenhum rolé aberto com os filtros atuais.', { exact: true })).toBeVisible();
  });

  test('CT06 — fila cheia promove o primeiro após saída de um confirmado', async ({ page, cenario }) => {
    const org = await cenario.criarConta('organizador');
    const confirmadoA = await cenario.criarConta('confirmadoA');
    const confirmadoB = await cenario.criarConta('confirmadoB');
    const primeiro = await cenario.criarConta('primeiroFila');
    const segundo = await cenario.criarConta('segundoFila');
    const evento = await cenario.criarEvento(org, { capacity: 3 });
    expect((await cenario.inscrever(evento, confirmadoA)).status).toBe('CONFIRMADO');
    expect((await cenario.inscrever(evento, confirmadoB)).status).toBe('CONFIRMADO');
    await loginPelaUI(page, primeiro);
    const card = page.getByTestId(`event-card-${evento.id}`);
    await card.getByRole('button', { name: /detalhes/i }).click();
    const modal = page.getByRole('dialog', { name: evento.titulo });
    const resposta = page.waitForResponse((r) => r.url().endsWith(`/api/eventos/${evento.id}/inscricao`) && r.request().method() === 'POST');
    await modal.getByRole('button', { name: 'Entrar na lista de espera', exact: true }).click();
    const response = await resposta;
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ eventoId: evento.id, usuarioId: primeiro.id, status: 'FILA' });
    await expect(modal.getByRole('button', { name: 'Sair da lista de espera', exact: true })).toBeVisible();
    let dto = await cenario.obterEvento(evento);
    expect(dto.confirmed).toHaveLength(3);
    expect(dto.confirmed).not.toContain(primeiro.nome);
    expect(dto.waitlist).toEqual([primeiro.nome]);
    expect((await cenario.inscrever(evento, segundo)).status).toBe('FILA');
    expect((await cenario.inscrever(evento, confirmadoA)).status).toBe('CANCELADO');
    dto = await cenario.obterEvento(evento);
    expect(dto.confirmed).toHaveLength(3);
    expect(dto.confirmed).toEqual(expect.arrayContaining([org.nome, confirmadoB.nome, primeiro.nome]));
    expect(dto.confirmed).not.toContain(confirmadoA.nome);
    expect(dto.waitlist).toEqual([segundo.nome]);
    await page.reload();
    await page.getByTestId(`event-card-${evento.id}`).getByRole('button', { name: /detalhes/i }).click();
    await expect(page.getByRole('dialog', { name: evento.titulo }).getByRole('button', { name: 'Cancelar presença', exact: true })).toBeVisible();
  });

  test('CT07 — visitante não encontra formulário e a API rejeita comentário', async ({ page, cenario }) => {
    const org = await cenario.criarConta('organizador');
    const visitante = await cenario.criarConta('visitante');
    const evento = await cenario.criarEvento(org);
    const texto = cenario.unico('nao-publicado');
    const comentariosAntes = (await cenario.obterEvento(evento)).comments;
    await loginPelaUI(page, visitante);
    await page.getByTestId(`event-card-${evento.id}`).getByRole('button', { name: /detalhes/i }).click();
    const modal = page.getByRole('dialog', { name: evento.titulo });
    await expect(modal.locator('form.comment-form')).toHaveCount(0);
    await expect(modal.getByText('Apenas participantes confirmados ou na lista de espera podem comentar.', { exact: true })).toBeVisible();
    expect((await cenario.comentario(evento, visitante, texto)).status()).toBe(409);
    const comentariosDepois = (await cenario.obterEvento(evento)).comments;
    expect(comentariosDepois).toEqual(comentariosAntes);
    expect(comentariosDepois).not.toEqual(expect.arrayContaining([expect.objectContaining({ text: texto })]));
  });

  test('CT08 — avaliação pendente de rolé próprio impede nova inscrição', async ({ page, cenario }) => {
    const org = await cenario.criarConta('organizador');
    const participante = await cenario.criarConta('pendente');
    const anterior = await cenario.criarEvento(org);
    const alvo = await cenario.criarEvento(org);
    expect((await cenario.inscrever(anterior, participante)).status).toBe('CONFIRMADO');
    await cenario.moverParaPassado(anterior);
    await cenario.marcarPresenca(anterior, participante);
    await cenario.concluir(anterior);
    await loginPelaUI(page, participante);
    const resposta = page.waitForResponse((r) => r.url().endsWith(`/api/eventos/${alvo.id}/inscricao`) && r.request().method() === 'POST');
    await page.getByTestId(`event-card-${alvo.id}`).getByRole('button', { name: 'Quero participar', exact: true }).click();
    const response = await resposta;
    expect(response.status()).toBe(409);
    expect((await response.json()).message).toMatch(/pendente|avaliar|anterior/i);
    await expect(page.locator('.notice.alert-error')).toContainText(/pendente|avaliar|anterior/i);
    const dto = await cenario.obterEvento(alvo);
    expect(dto.confirmed).toEqual([org.nome]);
    expect(dto.waitlist).toEqual([]);
  });

  test('CT09 — senha incorreta recebe 401 e mantém o login', async ({ page, cenario }) => {
    const conta = await cenario.criarConta('senhaErrada');
    await abrirPagina(page);
    await page.getByTestId('input-email').fill(conta.email);
    await page.getByTestId('input-password').fill('OutraSenhaIncorreta!');
    const resposta = page.waitForResponse((r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST');
    await page.getByTestId('btn-login').click();
    expect((await resposta).status()).toBe(401);
    await expect(page.locator('.login-error')).toContainText(/inválid|incorret/i);
    await expect(page.getByTestId('input-email')).toBeVisible();
    await expect(page.locator('section[aria-label="Feed de eventos"]')).toHaveCount(0);
  });

  test('CT10 — cadastro pela UI sem CPF cria sessão persistente', async ({ page, cenario, request }) => {
    const dados = cenario.dadosConta('cadastro');
    await abrirPagina(page);
    await page.getByTestId('link-cadastro').click();
    await page.getByTestId('input-nome').fill(dados.name);
    await page.getByTestId('input-email-cadastro').fill(dados.email);
    await page.getByTestId('input-nascimento').fill(dados.birthDate);
    await page.getByTestId('input-senha-cadastro').fill(dados.password);
    const resposta = page.waitForResponse((r) => r.url().endsWith('/api/auth/cadastro') && r.request().method() === 'POST');
    await page.getByTestId('btn-cadastrar').click();
    const response = await resposta;
    expect(response.status()).toBe(201);
    expect(response.request().postDataJSON().cpf).toBeNull();
    const { user, token } = await response.json();
    expect(user.id).toMatch(/^[a-f0-9-]{36}$/);
    expect(user).toMatchObject({ email: dados.email, fullName: dados.name, honor: 3 });
    await expect(page.locator('section[aria-label="Feed de eventos"]')).toBeVisible();
    const me = await request.get(cenario.endpoint('/auth/me'), { headers: { Authorization: `Bearer ${token}` } });
    expect(me.status()).toBe(200);
    expect((await me.json()).id).toBe(user.id);
    await page.reload();
    await expect(page.locator('section[aria-label="Feed de eventos"]')).toBeVisible();
  });

  test('CT11 — login demorado informa a espera e termina com sessão válida', async ({ page, cenario }) => {
    const conta = await cenario.criarConta('demora');
    await page.route('**/api/auth/login', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 6_000));
      await route.continue();
    });
    await abrirPagina(page);
    await page.getByTestId('input-email').fill(conta.email);
    await page.getByTestId('input-password').fill(conta.senha);
    const resposta = page.waitForResponse((r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST');
    await page.getByTestId('btn-login').click();
    await expect(page.getByRole('status')).toContainText(/servidor estava em repouso/i, { timeout: 8_000 });
    const response = await resposta;
    expect(response.status()).toBe(200);
    expect((await response.json()).user.id).toBe(conta.id);
    await expect(page.locator('section[aria-label="Feed de eventos"]')).toBeVisible();
    // No modo estático, a requisição do navegador vai direto à outra origem.
    if (process.env.E2E_MODE === 'static') {
      expect(new URL(response.url()).origin).toBe(apiURL.origin);
      expect(response.request().headers().origin).toBe(new URL(page.url()).origin);
      expect(response.headers()['access-control-allow-origin']).toBe(new URL(page.url()).origin);
    }
  });
});
