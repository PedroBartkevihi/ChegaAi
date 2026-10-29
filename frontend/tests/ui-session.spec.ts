import { test, expect, abrirPagina } from "./fixtures";

function deferred() {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve: resolve! };
}
const usuario = (id, name) => ({ id, name, fullName: `${name} Teste`, email: `${id}@exemplo.test`, honor: 3, isModerator: false, isAmbassador: false, capacityLimit: 20, canCreateEvents: true });
const ana = usuario("conta-a", "Ana");
const bruna = usuario("conta-b", "Bruna");
const evento = {
  id: "role-de-sessao", title: "Rolé para conferir sessão", category: "Futebol",
  date: new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10), time: "18:00", cep: "", place: "Praça de teste", distance: 0,
  capacity: 10, status: "ABERTO", organizer: { id: "organizador", name: "Organizador", honor: 3, isAmbassador: false },
  confirmed: ["Organizador", "Ana", "Bruna"], waitlist: [], comments: [], reports: 0, description: "Evento fictício para isolar respostas de sessões."
};
async function preparar(page, { profileGate = null, profileStatus = 200, comments = null, feedGate = null, eventData = evento, pendingRatings = { podeAvaliar: true, eventos: [] } } = {}) {
  await page.addInitScript(({ user }) => {
    localStorage.setItem("chegaai.token", "token-a");
    localStorage.setItem("chegaai.user", JSON.stringify(user));
    // As respostas precisam chegar para exercitar a guarda, mesmo sem cancelamento do fetch.
    AbortController.prototype.abort = function () {};
  }, { user: ana });
  let held = false;
  let heldFeed = false;
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace(/^.*\/api/, "");
    const token = request.headers().authorization;
    const current = token === "Bearer token-b" ? bruna : ana;
    const json = async (body, status = 200) => {
      try { await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body), headers: { "access-control-allow-origin": "*" } }); } catch {}
    };
    if (path === "/auth/login") return json({ token: "token-b", user: bruna });
    if (path === "/auth/me") {
      if (profileGate && token === "Bearer token-a" && !held) { held = true; profileGate.started.resolve(); await profileGate.release.promise; return json(profileStatus === 401 ? { message: "Token antigo" } : ana, profileStatus); }
      return json(current);
    }
    if (path === "/auth/me/inscricoes") return json([{ eventoId: evento.id, status: "CONFIRMADO", compareceu: false }]);
    if (path === "/auth/me/avaliacoes-pendentes") return json(pendingRatings);
    if (path === "/eventos") {
      if (feedGate) {
        if (heldFeed) return json({ message: "Falha do refresh" }, 503);
        heldFeed = true;
        feedGate.started.resolve();
        await feedGate.release.promise;
      }
      return json([eventData]);
    }
    if (path === `/eventos/${evento.id}/comentarios` && comments) {
      const gate = token === "Bearer token-a" ? comments.old : comments.current;
      gate.started.resolve();
      await gate.release.promise;
      return json({ author: current.name, text: request.postDataJSON().text, time: "18:00" }, 201);
    }
    return json({ status: "UP" });
  });
}
async function entrarEmBruna(page) {
  await page.locator(".sidebar-logout").click();
  await page.getByTestId("input-email").fill("b@exemplo.test");
  await page.getByTestId("input-password").fill("senha-ficticia123");
  await page.getByTestId("btn-login").click();
  await expect(page.locator(".sidebar-profile").getByText("Bruna", { exact: true })).toBeVisible();
}
for (const status of [200, 401]) {
  test(`resposta antiga de perfil (${status}) não restaura nem encerra a sessão nova`, async ({ page }) => {
    const gate = { started: deferred(), release: deferred() };
    await preparar(page, { profileGate: gate, profileStatus: status });
    await abrirPagina(page);
    await gate.started.promise;
    await entrarEmBruna(page);
    const oldResponse = page.waitForResponse((response) => response.url().endsWith("/auth/me") && response.request().headers().authorization === "Bearer token-a");
    gate.release.resolve();
    await oldResponse;
    await expect(page.locator(".sidebar-profile").getByText("Bruna", { exact: true })).toBeVisible();
    await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem("chegaai.user") || "null")?.id)).toBe(bruna.id);
    await expect.poll(() => page.evaluate(() => localStorage.getItem("chegaai.token"))).toBe("token-b");
  });
}
test("resposta e finally antigos não alteram comentário nem pending da nova sessão", async ({ page }) => {
  const comments = { old: { started: deferred(), release: deferred() }, current: { started: deferred(), release: deferred() } };
  await preparar(page, { comments });
  await abrirPagina(page);
  const open = async () => { await page.getByTestId(`event-card-${evento.id}`).getByRole("button", { name: "Detalhes", exact: true }).click(); return page.getByRole("dialog"); };
  let dialog = await open();
  await dialog.getByRole("textbox", { name: "Novo comentário" }).fill("Comentário antigo");
  await dialog.getByRole("button", { name: "Enviar comentário", exact: true }).click();
  await comments.old.started.promise;
  await dialog.getByRole("button", { name: "Fechar", exact: true }).click();
  await entrarEmBruna(page);
  dialog = await open();
  await dialog.getByRole("textbox", { name: "Novo comentário" }).fill("Comentário novo");
  await dialog.getByRole("button", { name: "Enviar comentário", exact: true }).click();
  await comments.current.started.promise;
  const oldResponse = page.waitForResponse((response) => response.url().endsWith("/comentarios") && response.request().headers().authorization === "Bearer token-a");
  comments.old.release.resolve();
  await oldResponse;
  await expect(dialog.getByRole("button", { name: "Enviando...", exact: true })).toHaveAttribute("aria-disabled", "true");
  await expect(dialog.getByRole("textbox", { name: "Novo comentário" })).toHaveValue("Comentário novo");
  comments.current.release.resolve();
  await expect(dialog.getByText("Comentário novo", { exact: true })).toBeVisible();
  await expect(dialog.getByText("Comentário antigo", { exact: true })).toHaveCount(0);
});

test("refresh que substitui o carregamento inicial e falha encerra a espera", async ({ page }) => {
  const gate = { started: deferred(), release: deferred() };
  await preparar(page, { feedGate: gate });
  await abrirPagina(page);
  await gate.started.promise;
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(page.getByRole("button", { name: "Tentar de novo", exact: true })).toBeVisible();
  const respostaAntiga = page.waitForResponse((response) => response.url().endsWith("/api/eventos") && response.status() === 200);
  gate.release.resolve();
  await respostaAntiga;
  await expect(page.getByTestId(`event-card-${evento.id}`)).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Tentar de novo", exact: true })).toBeVisible();
});

test("refresh de pendências preserva foco e texto da avaliação em andamento", async ({ page }) => {
  const pendingRatings = { podeAvaliar: true, eventos: [{ eventoId: evento.id, titulo: evento.title,
    data: evento.date, hora: evento.time, participantes: [{ id: bruna.id, nome: bruna.fullName }] }] };
  await preparar(page, { eventData: { ...evento, status: "CONCLUIDO" }, pendingRatings });
  await abrirPagina(page);
  await page.getByRole("button", { name: /Mostrar rolés anteriores/ }).click();
  await page.getByTestId(`event-card-${evento.id}`).getByRole("button", { name: "Detalhes", exact: true }).click();
  await page.getByRole("button", { name: "Avaliar participantes", exact: true }).click();
  const comentario = page.getByRole("textbox", { name: "Comentário (opcional)", exact: true });
  await expect(comentario).toBeEnabled();
  await comentario.fill("Minha avaliação em andamento");
  await expect(comentario).toBeFocused();
  const gate = { started: deferred(), release: deferred() };
  await page.route("**/api/auth/me/avaliacoes-pendentes", async (route) => {
    gate.started.resolve();
    await gate.release.promise;
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(pendingRatings), headers: { "access-control-allow-origin": "*" } });
  });
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await gate.started.promise;
  await expect(comentario).toBeEnabled();
  await expect(comentario).toBeFocused();
  const resposta = page.waitForResponse((response) => response.url().endsWith("/auth/me/avaliacoes-pendentes"));
  gate.release.resolve();
  await resposta;
  await expect(comentario).toBeEnabled();
  await expect(comentario).toBeFocused();
  await expect(comentario).toHaveValue("Minha avaliação em andamento");
});
