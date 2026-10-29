import { test, expect } from "./fixtures";

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
async function preparar(page, { profileGate = null, profileStatus = 200, comments = null } = {}) {
  await page.addInitScript(({ user }) => {
    localStorage.setItem("chegaai.token", "token-a");
    localStorage.setItem("chegaai.user", JSON.stringify(user));
    // As respostas precisam chegar para exercitar a guarda, mesmo sem cancelamento do fetch.
    AbortController.prototype.abort = function () {};
  }, { user: ana });
  let held = false;
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
    if (path === "/auth/me/avaliacoes-pendentes") return json({ podeAvaliar: true, eventos: [] });
    if (path === "/eventos") return json([evento]);
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
    await page.goto("/");
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
  await page.goto("/");
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
