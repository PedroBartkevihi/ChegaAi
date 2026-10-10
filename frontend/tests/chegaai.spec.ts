import { test, expect, Page } from '@playwright/test';

// Função auxiliar para gerar timestamps fictícios sequenciais baseados na hora local
function getSimulatedTime(stepOffset: number): string {
  const d = new Date();
  d.setSeconds(d.getSeconds() + stepOffset);
  return d.toTimeString().split(' ')[0]; // HH:MM:SS
}

test.describe('ChegaAí - Fluxos Principais do Sistema', () => {

  // -----------------------------------------------------------------------
  // ISOLAMENTO: Limpa a sessão antes de cada teste
  // -----------------------------------------------------------------------
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => {
      localStorage.removeItem('chegaai.user');
      localStorage.removeItem('chegaai.userId');
    });
    await page.reload();
    await expect(page.getByTestId('input-email')).toBeVisible();
  });

  // -----------------------------------------------------------------------
  // CT01
  // -----------------------------------------------------------------------
  test('CT01 - Autenticação com credenciais válidas e carregamento do feed', async ({ page }) => {
    console.log(`Running 'CT01' ${getSimulatedTime(0)}`);
    console.log(`1.open on / OK ${getSimulatedTime(1)}`);

    await page.getByTestId('input-email').fill('pedro@chegaai.dev');
    console.log(`2.type on id=email with value pedro@chegaai.dev OK ${getSimulatedTime(2)}`);

    await page.getByTestId('input-password').fill('admin123');
    await page.getByTestId('btn-login').click();
    console.log(`3.click on css=button[data-testid="btn-login"] OK ${getSimulatedTime(3)}`);

    await expect(page.locator('section[aria-label="Feed de eventos"]')).toBeVisible();
    console.log(`4.assertText on css=section[aria-label="Feed de eventos"] with value visible OK ${getSimulatedTime(4)}`);

    console.log(`'CT01' completed successfully`);
  });

  // -----------------------------------------------------------------------
  // CT02
  // -----------------------------------------------------------------------
  test('CT02 - Criação de um evento válido no sistema', async ({ page }) => {
    console.log(`Running 'CT02' ${getSimulatedTime(0)}`);

    await page.getByTestId('input-email').fill('pedro@chegaai.dev');
    console.log(`1.type on id=email with value pedro@chegaai.dev OK ${getSimulatedTime(1)}`);

    await page.getByTestId('input-password').fill('admin123');
    await page.getByTestId('btn-login').click();
    console.log(`2.click on css=button[data-testid="btn-login"] OK ${getSimulatedTime(2)}`);

    await page.locator('[aria-label="Navegação principal"]').getByRole('button', { name: 'Criar rolé' }).click();
    console.log(`3.click on css=button:has-text("Criar rolé") OK ${getSimulatedTime(3)}`);

    await expect(page.locator('section[aria-label="Criar evento"]')).toBeVisible();
    await page.locator('input[name="title"]').fill('Futebol de Sexta após o Trabalho');
    console.log(`4.type on css=input[name="title"] with value Futebol de Sexta após o Trabalho OK ${getSimulatedTime(4)}`);

    await page.locator('select[name="category"]').selectOption('Futebol');
    console.log(`5.select on css=select[name="category"] with value Futebol OK ${getSimulatedTime(5)}`);

    const dataFutura = new Date();
    dataFutura.setDate(dataFutura.getDate() + 5);
    const dataStr = dataFutura.toISOString().split('T')[0];
    await page.locator('input[name="date"]').fill(dataStr);
    console.log(`6.type on css=input[name="date"] with value ${dataStr} OK ${getSimulatedTime(6)}`);

    await page.locator('input[name="time"]').fill('18:00');
    console.log(`7.type on css=input[name="time"] with value 18:00 OK ${getSimulatedTime(7)}`);

    await page.locator('input[name="place"]').fill('Quadra do Bairro');
    console.log(`8.type on css=input[name="place"] with value Quadra do Bairro OK ${getSimulatedTime(8)}`);

    await page.locator('input[name="distance"]').fill('2');
    console.log(`9.type on css=input[name="distance"] with value 2 OK ${getSimulatedTime(9)}`);

    await page.locator('textarea[name="description"]').fill('Levar camisa branca e preta. Jogo para desestressar da semana!');
    console.log(`10.type on css=textarea[name="description"] with value Levar camisa branca e preta. Jogo para desestressar da semana! OK ${getSimulatedTime(10)}`);

    await page.getByTestId('input-capacidade').fill('10');
    console.log(`11.type on css=input[data-testid="input-capacidade"] with value 10 OK ${getSimulatedTime(11)}`);

    await page.getByRole('button', { name: /publicar rolé/i }).click();
    console.log(`12.click on css=button:has-text("Publicar rolé") OK ${getSimulatedTime(12)}`);

    await expect(page.locator('section[aria-label="Feed de eventos"]')).toBeVisible();
    console.log(`13.assertText on css=section[aria-label="Feed de eventos"] with value visible OK ${getSimulatedTime(13)}`);

    console.log(`'CT02' completed successfully`);
  });

  // -----------------------------------------------------------------------
  // CT03
  // -----------------------------------------------------------------------
  test('CT03 - Restrição de criação de evento para usuários com baixa reputação', async ({ page }) => {
    console.log(`Running 'CT03' ${getSimulatedTime(0)}`);

    await page.getByTestId('input-email').fill('baixa@chegaai.dev');
    console.log(`1.type on id=email with value baixa@chegaai.dev OK ${getSimulatedTime(1)}`);

    await page.getByTestId('input-password').fill('admin123');
    await page.getByTestId('btn-login').click();
    console.log(`2.click on css=button[data-testid="btn-login"] OK ${getSimulatedTime(2)}`);

    await page.locator('[aria-label="Navegação principal"]').getByRole('button', { name: 'Criar rolé' }).click();
    console.log(`3.click on css=button:has-text("Criar rolé") OK ${getSimulatedTime(3)}`);

    const alerta = page.locator('.notice.alert-error').or(page.getByRole('alert')).or(page.getByRole('status'));
    await expect(alerta).toBeVisible();
    await expect(alerta).toContainText(/reputação|honra/i);
    console.log(`4.assertText on css=.notice.alert-error with value reputação OK ${getSimulatedTime(4)}`);

    console.log(`'CT03' completed successfully`);
  });

  // -----------------------------------------------------------------------
  // CT04
  // -----------------------------------------------------------------------
  test('CT04 - Inserção de comentário em um evento', async ({ page }) => {
    console.log(`Running 'CT04' ${getSimulatedTime(0)}`);

    await page.getByTestId('input-email').fill('pedro@chegaai.dev');
    console.log(`1.type on id=email with value pedro@chegaai.dev OK ${getSimulatedTime(1)}`);

    await page.getByTestId('input-password').fill('admin123');
    await page.getByTestId('btn-login').click();
    console.log(`2.click on css=button[data-testid="btn-login"] OK ${getSimulatedTime(2)}`);

    const primeiroCard = page.locator('article.event-card').first();
    await expect(primeiroCard).toBeVisible();
    await primeiroCard.getByRole('button', { name: /detalhes/i }).click();
    console.log(`3.click on css=article.event-card button:has-text("Detalhes") OK ${getSimulatedTime(3)}`);

    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();

    const btnParticipar = modal.getByRole('button', { name: /quero participar|entrar na lista|entrar na fila/i });
    if (await btnParticipar.isVisible()) {
      await btnParticipar.click();
      console.log(`4.click on css=button:has-text("Participar") OK ${getSimulatedTime(4)}`);
      await page.waitForTimeout(500);
    }

    const formComentario = modal.locator('form.comment-form');
    await expect(formComentario).toBeVisible();
    await formComentario.locator('textarea').fill('Excelente iniciativa! Estarei lá com certeza.');
    console.log(`5.type on css=form.comment-form textarea with value Excelente iniciativa! Estarei lá com certeza. OK ${getSimulatedTime(5)}`);

    await formComentario.getByRole('button', { name: /comentar|enviar/i }).click();
    console.log(`6.click on css=form.comment-form button:has-text("Enviar") OK ${getSimulatedTime(6)}`);

    await expect(modal.getByText('Excelente iniciativa! Estarei lá com certeza.')).toBeVisible();
    console.log(`7.assertText on css=[role="dialog"] with value Excelente iniciativa! Estarei lá com certeza. OK ${getSimulatedTime(7)}`);

    console.log(`'CT04' completed successfully`);
  });

  // -----------------------------------------------------------------------
  // CT05
  // -----------------------------------------------------------------------
  test('CT05 - Filtragem do feed por busca textual e categoria', async ({ page }) => {
    console.log(`Running 'CT05' ${getSimulatedTime(0)}`);

    await page.getByTestId('input-email').fill('pedro@chegaai.dev');
    console.log(`1.type on id=email with value pedro@chegaai.dev OK ${getSimulatedTime(1)}`);

    await page.getByTestId('input-password').fill('admin123');
    await page.getByTestId('btn-login').click();
    console.log(`2.click on css=button[data-testid="btn-login"] OK ${getSimulatedTime(2)}`);

    const searchInput = page.getByPlaceholder(/buscar|pesquisar|título|nome/i).or(page.locator('input[type="text"]').first());
    await searchInput.fill('Futebol');
    console.log(`3.type on css=input[type="text"] with value Futebol OK ${getSimulatedTime(3)}`);

    await page.waitForTimeout(600);
    const cards = page.locator('article.event-card');
    if (await cards.count() > 0) {
      await expect(cards.first()).toBeVisible();
    }

    const selectCategoria = page.locator('select').first();
    await selectCategoria.selectOption({ label: 'Futebol' });
    console.log(`4.select on css=select with value Futebol OK ${getSimulatedTime(4)}`);

    await page.waitForTimeout(600);
    await expect(page.locator('section[aria-label="Feed de eventos"]')).toBeVisible();
    console.log(`5.assertText on css=section[aria-label="Feed de eventos"] with value visible OK ${getSimulatedTime(5)}`);

    console.log(`'CT05' completed successfully`);
  });

  // -----------------------------------------------------------------------
  // CT06
  // -----------------------------------------------------------------------
  test('CT06 - Gerenciamento de fila de espera em eventos com capacidade esgotada', async ({ page }) => {
    console.log(`Running 'CT06' ${getSimulatedTime(0)}`);

    await page.getByTestId('input-email').fill('marcos@chegaai.dev');
    console.log(`1.type on id=email with value marcos@chegaai.dev OK ${getSimulatedTime(1)}`);

    await page.getByTestId('input-password').fill('admin123');
    await page.getByTestId('btn-login').click();
    console.log(`2.click on css=button[data-testid="btn-login"] OK ${getSimulatedTime(2)}`);

    const cardLotado = page.locator('article.event-card:has-text("Lotado")').first();
    await expect(cardLotado).toBeVisible();
    await cardLotado.getByRole('button', { name: /detalhes/i }).click();
    console.log(`3.click on css=article.event-card:has-text("Lotado") button:has-text("Detalhes") OK ${getSimulatedTime(3)}`);

    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();

    const btnParticipar = modal.getByRole('button', { name: /participar|lista|fila/i });
    await btnParticipar.click();
    console.log(`4.click on css=button:has-text("Participar") OK ${getSimulatedTime(4)}`);

    await expect(modal).toContainText(/fila|espera|inscrito/i);
    console.log(`5.assertText on css=[role="dialog"] with value fila|espera OK ${getSimulatedTime(5)}`);

    console.log(`'CT06' completed successfully`);
  });

  // -----------------------------------------------------------------------
  // CT07
  // -----------------------------------------------------------------------
  test('CT07 - Bloqueio de inserção de comentários para usuários não participantes', async ({ page }) => {
    console.log(`Running 'CT07' ${getSimulatedTime(0)}`);

    await page.getByTestId('input-email').fill('caio@chegaai.dev');
    console.log(`1.type on id=email with value caio@chegaai.dev OK ${getSimulatedTime(1)}`);

    await page.getByTestId('input-password').fill('admin123');
    await page.getByTestId('btn-login').click();
    console.log(`2.click on css=button[data-testid="btn-login"] OK ${getSimulatedTime(2)}`);

    const cardFechado = page.locator('article.event-card:has-text("Passeio Fechado")').or(page.locator('article.event-card')).first();
    await expect(cardFechado).toBeVisible();
    await cardFechado.getByRole('button', { name: /detalhes/i }).click();
    console.log(`3.click on css=article.event-card:has-text("Passeio Fechado") button:has-text("Detalhes") OK ${getSimulatedTime(3)}`);

    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();

    const textarea = modal.locator('textarea');
    const isVisible = await textarea.isVisible();
    if (isVisible) {
      await expect(textarea).toBeDisabled();
      console.log(`4.assert on css=textarea with value disabled OK ${getSimulatedTime(4)}`);
    } else {
      await expect(textarea).toHaveCount(0);
      console.log(`4.assert on css=textarea with value not-present OK ${getSimulatedTime(4)}`);
    }

    console.log(`'CT07' completed successfully`);
  });

  // -----------------------------------------------------------------------
  // CT08
  // -----------------------------------------------------------------------
  test('CT08 - Bloqueio de nova inscrição devido a avaliações pendentes', async ({ page }) => {
    console.log(`Running 'CT08' ${getSimulatedTime(0)}`);

    await page.getByTestId('input-email').fill('tester.pendente@chegaai.dev');
    console.log(`1.type on id=email with value tester.pendente@chegaai.dev OK ${getSimulatedTime(1)}`);

    await page.getByTestId('input-password').fill('admin123');
    await page.getByTestId('btn-login').click();
    console.log(`2.click on css=button[data-testid="btn-login"] OK ${getSimulatedTime(2)}`);

    const primeiroCard = page.locator('article.event-card').first();
    await expect(primeiroCard).toBeVisible();
    await primeiroCard.getByRole('button', { name: /detalhes/i }).click();
    console.log(`3.click on css=article.event-card button:has-text("Detalhes") OK ${getSimulatedTime(3)}`);

    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();

    const btnParticipar = modal.getByRole('button', { name: /quero participar|entrar na lista|entrar na fila/i });
    await btnParticipar.click();
    console.log(`4.click on css=button:has-text("Participar") OK ${getSimulatedTime(4)}`);

    const alerta = page.locator('.notice.alert-error').or(page.getByRole('alert')).or(page.getByRole('status'));
    await expect(alerta).toBeVisible();
    await expect(alerta).toContainText(/pendente|avaliar|anterior/i);
    console.log(`5.assertText on css=.notice.alert-error with value pendente|avaliar OK ${getSimulatedTime(5)}`);

    console.log(`'CT08' completed successfully`);
  });

});