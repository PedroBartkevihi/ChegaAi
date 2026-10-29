import test from "node:test";
import assert from "node:assert/strict";
import { createSessionGuard, StaleSessionError, isDiscardedRequest } from "../src/lib/session.js";
import { api, TOKEN_KEY, SESSION_EXPIRED_EVENT } from "../src/lib/api.js";

test("trocar a sessão invalida pedidos anteriores, mesmo com o mesmo token", () => {
  let storedToken = "token-a";
  const guard = createSessionGuard(() => storedToken);
  const first = guard.replace(storedToken);
  assert.equal(guard.isCurrent(first), true);
  const second = guard.replace(storedToken);
  assert.equal(first.controller.signal.aborted, true);
  assert.equal(guard.isCurrent(first), false);
  assert.equal(guard.isCurrent(second), true);
  storedToken = "token-b";
  assert.equal(guard.isCurrent(second), false);
  const third = guard.replace(storedToken);
  guard.dispose();
  assert.equal(guard.isCurrent(third), false);
  assert.equal(third.controller.signal.aborted, true);
  assert.equal(isDiscardedRequest(new StaleSessionError()), true);
});

test("401 atrasado não encerra token novo; 401 atual encerra a sessão correspondente", async () => {
  const saved = { fetch: globalThis.fetch, localStorage: globalThis.localStorage, window: globalThis.window };
  let storedToken = "token-a";
  const events = [];
  globalThis.localStorage = { getItem: (key) => key === TOKEN_KEY ? storedToken : null };
  globalThis.window = { dispatchEvent: (event) => events.push(event) };
  let release;
  globalThis.fetch = () => new Promise((resolve) => { release = resolve; });
  try {
    const old = api("/auth/me");
    storedToken = "token-b";
    release(new Response(JSON.stringify({ message: "Token antigo" }), { status: 401 }));
    await assert.rejects(old, { status: 401 });
    assert.equal(events.length, 0);
    globalThis.fetch = async () => new Response(JSON.stringify({ message: "Sessão expirada" }), { status: 401 });
    await assert.rejects(api("/auth/me"), { status: 401 });
    assert.equal(events.length, 1);
    assert.equal(events[0].type, SESSION_EXPIRED_EVENT);
    assert.equal(events[0].detail.token, "token-b");
  } finally {
    Object.assign(globalThis, saved);
  }
});

test("geração antiga não encerra sessão com token igual e opções de controle não vão ao fetch", async () => {
  const saved = { fetch: globalThis.fetch, localStorage: globalThis.localStorage, window: globalThis.window };
  const events = [];
  let options;
  globalThis.localStorage = { getItem: () => "token-a" };
  globalThis.window = { dispatchEvent: (event) => events.push(event) };
  globalThis.fetch = async (_url, sent) => {
    options = sent;
    return new Response(JSON.stringify({ message: "Sem permissão" }), { status: 401 });
  };
  try {
    await assert.rejects(api("/auth/me", { token: "token-a", isCurrentSession: () => false }), { status: 401 });
    assert.equal(events.length, 0);
    assert.equal(options.headers.Authorization, "Bearer token-a");
    assert.equal("isCurrentSession" in options, false);
    assert.equal("token" in options, false);
    await assert.rejects(api("/auth/login", { semToken: true }), { status: 401 });
    assert.equal(options.headers.Authorization, undefined);
    assert.equal(events.length, 0);
  } finally {
    Object.assign(globalThis, saved);
  }
});
