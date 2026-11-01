import test from "node:test";
import assert from "node:assert/strict";
import { EMPTY_SECTION, PAGE_SIZE, appendPage, feedPath, refreshPages, toSummary, withCounts } from "../src/lib/feed.js";

const filtros = (mudancas = {}) => ({ search: "", category: "todos", honor: "0", ...mudancas });
const parametros = (path) => Object.fromEntries(new URL(path, "http://exemplo.test").searchParams);

test("o caminho do feed leva só os filtros escolhidos", () => {
  assert.ok(feedPath("proximos", filtros()).startsWith("/eventos/feed?"));
  assert.deepEqual(parametros(feedPath("proximos", filtros())), { secao: "proximos", pagina: "0", tamanho: String(PAGE_SIZE) });
  assert.deepEqual(parametros(feedPath("anteriores", filtros({ category: "Patinação", honor: "4", search: "  orla & sol  " }), 2)),
    { secao: "anteriores", pagina: "2", tamanho: "12", categoria: "Patinação", honraMinima: "4", busca: "orla & sol" });
  assert.deepEqual(parametros(feedPath("proximos", filtros({ honor: "ambassador", search: "   " }), 0, 36)),
    { secao: "proximos", pagina: "0", tamanho: "36", embaixador: "true" });
});

test("a atualização repete as páginas abertas numa chamada, até quatro", () => {
  assert.equal(refreshPages(EMPTY_SECTION), 1);
  assert.equal(refreshPages({ ...EMPTY_SECTION, pages: 3 }), 3);
  assert.equal(refreshPages({ ...EMPTY_SECTION, pages: 9 }), 4);
});

test("a página seguinte não repete rolés que já estão na tela", () => {
  const atual = [{ id: "a" }, { id: "b" }];
  assert.deepEqual(appendPage(atual, [{ id: "b" }, { id: "c" }]).map((item) => item.id), ["a", "b", "c"]);
});

test("o detalhe vira resumo com as contagens do card", () => {
  const detalhe = { id: "x", title: "Rolé", cep: "20000-000", confirmed: ["Ana", "Bia"], waitlist: ["Caio"], comments: [{ text: "oi" }] };
  assert.deepEqual(withCounts(detalhe), { ...detalhe, confirmedCount: 2, waitlistCount: 1 });
  assert.deepEqual(toSummary(detalhe), { id: "x", title: "Rolé", confirmedCount: 2, waitlistCount: 1 });
  const resumo = { id: "y", confirmedCount: 1, waitlistCount: 0 };
  assert.equal(toSummary(resumo), resumo);
});
