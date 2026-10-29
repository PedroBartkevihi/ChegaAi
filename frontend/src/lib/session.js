// Cada entrada/saída cria uma geração, mesmo quando a pessoa entra de novo na mesma conta.
export class StaleSessionError extends Error {
  constructor() {
    super("A sessão desta requisição já foi substituída.");
    this.name = "StaleSessionError";
  }
}
export function isDiscardedRequest(error) {
  return error?.name === "AbortError" || error instanceof StaleSessionError;
}
export function createSessionGuard(readToken) {
  let current = { generation: 0, token: null, controller: null };
  return {
    capture: () => current,
    isCurrent: (scope) => scope === current && scope.token === readToken(),
    replace(token) {
      current.controller?.abort();
      current = { generation: current.generation + 1, token, controller: new AbortController() };
      return current;
    },
    dispose() {
      current.controller?.abort();
      current = { generation: current.generation + 1, token: null, controller: null };
    },
  };
}
