// Feed paginado: a API (GET /eventos/feed) filtra, ordena e separa próximos e anteriores; a tela pede 12 por vez.
export const PAGE_SIZE = 12;
// A busca espera uma pausa na digitação antes de ir à API.
export const SEARCH_DELAY = 300;
// Ao voltar para a aba, as páginas já abertas voltam numa chamada só, até 4 (48 rolés; a API aceita até 50).
const MAX_REFRESH_PAGES = 4;
export const EMPTY_SECTION = { items: [], pages: 0, total: 0, hasNext: false, state: "idle", more: "idle" };

export function feedPath(section, filters, page = 0, size = PAGE_SIZE) {
  const params = new URLSearchParams({ secao: section, pagina: String(page), tamanho: String(size) });
  if (filters.category !== "todos") params.set("categoria", filters.category);
  if (filters.honor === "ambassador") params.set("embaixador", "true");
  else if (filters.honor !== "0") params.set("honraMinima", filters.honor);
  const search = filters.search.trim();
  if (search) params.set("busca", search);
  return `/eventos/feed?${params}`;
}
export function refreshPages(section) {
  return Math.min(Math.max(section.pages, 1), MAX_REFRESH_PAGES);
}
// Um rolé criado entre uma página e outra empurra a lista: o que já está na tela não se repete.
export function appendPage(items, incoming) {
  const seen = new Set(items.map((item) => item.id));
  return [...items, ...incoming.filter((item) => !seen.has(item.id))];
}
// O detalhe (GET /eventos/{id}) traz os nomes de confirmados e da fila; a tela usa só as contagens, como as do feed.
export function withCounts(event) {
  return { ...event, confirmedCount: event.confirmed.length, waitlistCount: event.waitlist.length };
}
export function toSummary(event) {
  if ("confirmedCount" in event) return event;
  const { confirmed, waitlist, comments, reports, cep, ...summary } = withCounts(event);
  return summary;
}
