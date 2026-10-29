import { test, expect, loginPelaUI } from "./fixtures";

async function abrirRole(page, evento, passado = false) {
  if (passado) await page.getByRole("button", { name: /Mostrar rolés anteriores/ }).click();
  await page.getByTestId(`event-card-${evento.id}`).getByRole("button", { name: "Detalhes", exact: true }).click();
  return page.getByRole("dialog", { name: evento.titulo, exact: true });
}

test("homônimos têm inscrições e permissão de comentar independentes", async ({ page, cenario, request }) => {
  const organizador = await cenario.criarConta("Organizador");
  async function criarHomonimo() {
    const dados = { ...cenario.dadosConta("Pedro"), name: "Pedro Homônimo" };
    const resposta = await request.post(cenario.endpoint("/auth/cadastro"), { data: dados });
    expect(resposta.status()).toBe(201);
    const { user, token } = await resposta.json();
    expect(user.fullName).toBe(dados.name);
    return { id: user.id, nome: user.name, email: dados.email, senha: dados.password, token };
  }
  const primeiroPedro = await criarHomonimo();
  const segundoPedro = await criarHomonimo();
  expect(primeiroPedro.nome).toBe(segundoPedro.nome);
  expect(primeiroPedro.id).not.toBe(segundoPedro.id);
  const evento = await cenario.criarEvento(organizador);
  await cenario.inscrever(evento, primeiroPedro);
  await loginPelaUI(page, segundoPedro);
  const dialogo = await abrirRole(page, evento);
  await expect(dialogo.getByRole("button", { name: "Quero participar", exact: true })).toBeEnabled();
  await expect(dialogo.locator("form.comment-form")).toHaveCount(0);
  await dialogo.getByRole("button", { name: "Quero participar", exact: true }).click();
  await expect(dialogo.locator("form.comment-form")).toBeVisible();
  await expect(dialogo.getByRole("button", { name: "Cancelar presença", exact: true })).toBeEnabled();
  await expect.poll(async () => (await cenario.obterEvento(evento)).confirmed.length).toBe(3);
});

test("organizador salva e reverte presença antes de concluir com resumo das ausências", async ({ page, cenario }) => {
  const organizador = await cenario.criarConta("Organizador");
  const presente = await cenario.criarConta("Presente");
  const ausente = await cenario.criarConta("Ausente");
  const evento = await cenario.criarEvento(organizador);
  await cenario.inscrever(evento, presente);
  await cenario.inscrever(evento, ausente);
  await cenario.moverParaPassado(evento);
  await loginPelaUI(page, organizador);
  let dialogo = await abrirRole(page, evento, true);
  let linha = dialogo.locator(".participant-row").filter({ hasText: presente.nome });
  await linha.getByRole("checkbox").click();
  await expect(linha.getByRole("checkbox")).toBeChecked();
  await expect(linha.getByRole("checkbox")).toBeEnabled();
  await linha.getByRole("checkbox").click();
  await expect(linha.getByRole("checkbox")).not.toBeChecked();
  await expect(linha.getByRole("checkbox")).toBeEnabled();
  await page.reload();
  dialogo = await abrirRole(page, evento, true);
  linha = dialogo.locator(".participant-row").filter({ hasText: presente.nome });
  await expect(linha.getByRole("checkbox")).not.toBeChecked();
  await linha.getByRole("checkbox").click();
  await expect(linha.getByRole("checkbox")).toBeChecked();
  await expect(dialogo.getByRole("button", { name: "Concluir rolé", exact: true })).toBeEnabled();
  await expect(dialogo.locator(".participant-row").filter({ hasText: organizador.nome }).getByRole("checkbox")).toHaveCount(0);
  await dialogo.getByRole("button", { name: "Concluir rolé", exact: true }).click();
  const resumo = dialogo.getByRole("region", { name: "Confirmar conclusão" });
  await expect(resumo).toContainText(ausente.nome);
  await expect(resumo).toContainText("Cada ausência desconta");
  await expect(page.getByRole("dialog")).toHaveCount(1);
  await expect(page.getByRole("alertdialog")).toHaveCount(0);
  await resumo.getByRole("button", { name: "Confirmar conclusão", exact: true }).click();
  await expect(dialogo.locator(".participant-row").filter({ hasText: ausente.nome })).toContainText("Ausente");
  await expect(dialogo.locator(".participant-row").filter({ hasText: presente.nome }).getByRole("checkbox")).toBeDisabled();
});

test("última avaliação remove pendência e permite nova inscrição sem recarregar", async ({ page, cenario }) => {
  const organizador = await cenario.criarConta("Organizador");
  const participante = await cenario.criarConta("Participante");
  const evento = await cenario.criarEvento(organizador);
  const proximo = await cenario.criarEvento(organizador);
  await cenario.inscrever(evento, participante);
  await cenario.moverParaPassado(evento);
  await cenario.marcarPresenca(evento, participante);
  await cenario.concluir(evento);
  await loginPelaUI(page, participante);
  await page.getByRole("navigation", { name: "Navegação principal" }).getByRole("button", { name: "Meu perfil", exact: true }).click();
  const pendencias = page.getByRole("region", { name: "Avaliações pendentes", exact: true });
  const formulario = pendencias.getByRole("form").filter({ hasText: organizador.nome });
  await expect(formulario.getByRole("combobox")).toBeEnabled();
  await formulario.getByRole("combobox").selectOption("5");
  await formulario.getByRole("textbox", { name: "Comentário (opcional)" }).fill("Bom encontro.");
  await formulario.getByRole("button", { name: "Enviar avaliação", exact: true }).click();
  await expect(pendencias).toContainText("Nenhuma avaliação pendente");
  await page.getByRole("navigation", { name: "Navegação principal" }).getByRole("button", { name: "Feed", exact: true }).click();
  const card = page.getByTestId(`event-card-${proximo.id}`);
  await expect(card.getByRole("button", { name: "Quero participar", exact: true })).toBeEnabled();
  await card.getByRole("button", { name: "Quero participar", exact: true }).click();
  await expect(card.getByRole("button", { name: "Cancelar presença", exact: true })).toBeEnabled();
});

test("honra e permissão de criar atualizam ao abrir perfil na mesma sessão", async ({ page, cenario, request }) => {
  const organizador = await cenario.criarConta("Organizador");
  const alvo = await cenario.criarConta("Alvo");
  const outros = [await cenario.criarConta("Avaliador1"), await cenario.criarConta("Avaliador2")];
  const evento = await cenario.criarEvento(organizador, { capacity: 4 });
  for (const pessoa of [alvo, ...outros]) await cenario.inscrever(evento, pessoa);
  await cenario.moverParaPassado(evento);
  for (const pessoa of [alvo, ...outros]) await cenario.marcarPresenca(evento, pessoa);
  await cenario.concluir(evento);
  await loginPelaUI(page, alvo);
  const nav = page.getByRole("navigation", { name: "Navegação principal" });
  await nav.getByRole("button", { name: "Meu perfil", exact: true }).click();
  await expect(page.getByTestId("honor-value")).toHaveText("3.0");
  await nav.getByRole("button", { name: "Feed", exact: true }).click();
  for (const avaliador of [organizador, ...outros]) {
    const resposta = await request.post(cenario.endpoint(`/eventos/${evento.id}/avaliacoes`), {
      headers: cenario.auth(avaliador), data: { avaliadoId: alvo.id, nota: 1 },
    });
    expect(resposta.status()).toBe(201);
  }
  await nav.getByRole("button", { name: "Meu perfil", exact: true }).click();
  await expect(page.getByTestId("honor-value")).toHaveText("1.0");
  await nav.getByRole("button", { name: "Criar rolé", exact: true }).click();
  await expect(page.getByRole("region", { name: "Criar evento", exact: true })).toHaveCount(0);
  await expect(page.getByText("Sua conta não pode criar rolés no momento.", { exact: false })).toBeVisible();
  const salvo = await page.evaluate(() => JSON.parse(localStorage.getItem("chegaai.user") || "null"));
  expect(salvo.id).toBe(alvo.id);
  expect(salvo.honor).toBe(1);
  expect(salvo.canCreateEvents).toBe(false);
});
