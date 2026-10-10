export const categories = ["Futebol", "Pets", "Patinação", "Passeio", "Esporte", "Cultura", "Lazer"];
export const initialFilters = {
  search: "",
  category: "todos",
  distance: "todos",
  honor: "0",
};
export function getEnrollmentActionLabel(event, enrollment) {
  if (event.status === "TRAVADO") return "Suspenso";
  if (event.status === "CANCELADO") return "Cancelado";
  if (enrollment === "CONFIRMADO") return "Cancelar presença";
  if (enrollment === "FILA") return "Sair da lista de espera";
  return event.confirmed.length >= event.capacity ? "Entrar na lista de espera" : "Quero participar";
}
export function buildFilterSummary(filters) {
  const parts = [];
  if (filters.category !== "todos") parts.push(filters.category);
  if (filters.distance !== "todos") parts.push(`até ${filters.distance} km`);
  if (filters.honor !== "0") parts.push(`honra ${filters.honor}+`);
  if (filters.search) parts.push(`busca "${filters.search}"`);
  return parts.length ? parts.join(" · ") : "Todos os rolés";
}
export function isAmbassador(honor) {
  return honor >= 4.8;
}
export function createId() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return `id-${Date.now()}-${Math.round(Math.random() * 10000)}`;
}
export function formatDate(date) {
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date(`${date}T12:00:00`));
}
export function todayInputValue() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().split("T")[0];
}
