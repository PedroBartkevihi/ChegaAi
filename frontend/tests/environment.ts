// Nenhuma fixture pode enviar dados para uma instalação externa.
export function urlLocal(value: string, nome: string): URL {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol)
      || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
      || url.username || url.password || url.search || url.hash) {
    throw new Error(`${nome} deve ser uma URL HTTP de loopback sem credenciais, query ou fragmento.`);
  }
  return url;
}

export const frontendURL = urlLocal(process.env.E2E_BASE_URL || 'http://127.0.0.1:3000', 'E2E_BASE_URL');
if (frontendURL.pathname !== '/') throw new Error('E2E_BASE_URL deve apontar para a raiz.');
export const apiURL = urlLocal(process.env.E2E_API_BASE_URL || 'http://127.0.0.1:8080/api', 'E2E_API_BASE_URL');
if (apiURL.pathname !== '/api') throw new Error('E2E_API_BASE_URL deve terminar em /api, sem barra final.');

export function databaseURL(): string {
  const value = process.env.E2E_DATABASE_URL;
  if (!value) throw new Error('Defina E2E_DATABASE_URL para o banco descartável local chegaai_e2e.');
  const url = new URL(value);
  // Query parameters do pg podem substituir host/database da própria URL.
  if (!['postgres:', 'postgresql:'].includes(url.protocol)
      || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
      || url.pathname !== '/chegaai_e2e' || url.search || url.hash) {
    throw new Error('E2E_DATABASE_URL aceita somente PostgreSQL em loopback, database chegaai_e2e e sem query.');
  }
  return value;
}
