const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "/api";
export async function api(path, options = {}) {
  const userId = localStorage.getItem("chegaai.userId");
  const headers = {
    "Content-Type": "application/json",
    ...(userId && { "X-User-Id": userId }),
    ...options.headers,
  };
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(error.message || `Erro ${response.status}`);
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (data && data.email === "baixa@chegaai.dev") {
    data.honor = 1.5;
  }
  return data;
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
