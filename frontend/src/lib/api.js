const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "/api";
export const TOKEN_KEY = "chegaai.token";
export const USER_KEY = "chegaai.user";
export const SESSION_EXPIRED_EVENT = "chegaai:sessao-expirada";
export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}
// semToken: login e cadastro vão sem o token guardado. Um token antigo (de conta apagada) faria a API
// responder 401 antes mesmo de olhar e-mail e senha.
export async function api(path, { semToken = false, token: requestedToken, isCurrentSession, ...options } = {}) {
  const token = semToken ? null : (requestedToken === undefined ? localStorage.getItem(TOKEN_KEY) : requestedToken);
  const headers = {
    "Content-Type": "application/json",
    ...(token && { Authorization: `Bearer ${token}` }),
    ...options.headers,
  };
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (!response.ok) {
    // Um pedido antigo não pode encerrar uma sessão iniciada enquanto ele estava em trânsito.
    if (response.status === 401 && token && token === localStorage.getItem(TOKEN_KEY)
        && (!isCurrentSession || isCurrentSession())) {
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT, { detail: { token } }));
    }
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw new ApiError(error.message || `Erro ${response.status}`, response.status);
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
export function cleanErrorMessage(msg) {
  if (!msg) return "Ocorreu um erro inesperado. Tente novamente.";
  let clean = msg;
  clean = clean.replace(/RN\d+/gi, "").replace(/UC\d+/gi, "");
  if (clean.includes("honra") || clean.includes("Honra") || clean.includes("reputacao") || clean.includes("Reputação")) {
    if (clean.toLowerCase().includes("abaixo") || clean.toLowerCase().includes("insuficiente") || clean.toLowerCase().includes("limite")) {
      return "Ação Não Permitida. Sua reputação atual na plataforma está abaixo do limite mínimo necessário para realizar esta ação.";
    }
  }
  if (clean.includes("vagas") || clean.includes("capacidade") || clean.includes("lotado")) {
    return "Limite de vagas atingido. O evento está lotado no momento.";
  }
  clean = clean.replace(/^[\s\-\:\⚠️\❌]+/, "").trim();
  if (clean) {
    clean = clean.charAt(0).toUpperCase() + clean.slice(1);
  }
  return clean;
}
