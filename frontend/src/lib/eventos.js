export const categories = ["Futebol", "Pets", "Patinação", "Passeio", "Esporte", "Cultura", "Lazer"];
// Sem filtro de distância: a API devolve a distância sempre 0 (ver EventoService), então ele não filtrava nada.
export const initialFilters = {
  search: "",
  category: "todos",
  honor: "0",
};
// Os rolés são no Rio: datas e horários da API são do horário de Brasília (sem horário de verão desde 2019).
const TIME_ZONE = "America/Sao_Paulo";
const UTC_OFFSET = "-03:00";
export function getEnrollmentActionLabel(event, enrollment) {
  if (event.status === "TRAVADO") return "Suspenso";
  if (event.status === "CANCELADO") return "Cancelado";
  if (event.status === "CONCLUIDO") return "Concluído";
  if (enrollment === "CONFIRMADO") return "Cancelar presença";
  if (enrollment === "FILA") return "Sair da lista de espera";
  return event.confirmed.length >= event.capacity ? "Entrar na lista de espera" : "Quero participar";
}
export function hasActiveFilters(filters) {
  return filters.search !== "" || filters.category !== "todos" || filters.honor !== "0";
}
export function buildFilterSummary(filters) {
  const parts = [];
  if (filters.category !== "todos") parts.push(filters.category);
  if (filters.honor !== "0") parts.push(`honra ${filters.honor}+`);
  if (filters.search) parts.push(`busca "${filters.search}"`);
  return parts.length ? parts.join(" · ") : "Todos os rolés";
}
// "Patinação" -> "patinacao": vira a classe .cat-patinacao, que define as cores da categoria.
export function categorySlug(category) {
  return (category || "")
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-");
}
export function isAmbassador(honor) {
  return honor >= 4.8;
}
export function createId() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return `id-${Date.now()}-${Math.round(Math.random() * 10000)}`;
}
export function eventTimestamp(event) {
  return new Date(`${event.date}T${event.time || "00:00"}:00${UTC_OFFSET}`).getTime();
}
// Aberto e com início ainda por vir. Passado o horário de início, o rolé vai para "anteriores":
// a API não informa a duração, então não dá para saber se ainda está acontecendo.
export function isUpcoming(event, now = Date.now()) {
  return event.status === "ABERTO" && eventTimestamp(event) >= now;
}
// Data do rolé (sáb., 30 de out. / Hoje / Amanhã) e lista em ordem de data, Embaixador como desempate.
export function sortByDate(events) {
  return [...events].sort((a, b) => eventTimestamp(a) - eventTimestamp(b) || b.organizer.honor - a.organizer.honor);
}
function dayKey(date) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
}
export function formatEventDay(date, now = new Date()) {
  if (date === dayKey(now)) return "Hoje";
  if (date === dayKey(new Date(now.getTime() + 24 * 60 * 60 * 1000))) return "Amanhã";
  const sameYear = date.slice(0, 4) === dayKey(now).slice(0, 4);
  return new Intl.DateTimeFormat("pt-BR", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    ...(sameYear ? {} : { year: "numeric" }),
    timeZone: "UTC",
  }).format(new Date(`${date}T12:00:00Z`));
}
export function formatEventDateLong(date) {
  return new Intl.DateTimeFormat("pt-BR", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${date}T12:00:00Z`));
}
export function todayInputValue() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().split("T")[0];
}
