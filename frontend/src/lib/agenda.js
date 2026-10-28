import { categorySlug, eventTimestamp } from "./eventos";
// Links e arquivos para levar o rolé para fora do site: mapa e agenda (.ics).
export function mapUrl(event) {
  const query = [event.place, event.cep].filter(Boolean).join(", ");
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`;
}
function icsText(text) {
  return String(text || "")
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n");
}
// O formato pede linhas de até 75 bytes; as maiores continuam na linha seguinte, começando com espaço.
function foldLine(line) {
  const encoder = new TextEncoder();
  const parts = [];
  let current = "";
  for (const char of line) {
    const limit = parts.length ? 74 : 75;
    if (encoder.encode(current + char).length > limit) {
      parts.push(current);
      current = "";
    }
    current += char;
  }
  parts.push(current);
  return parts.join("\r\n ");
}
function icsUtc(timestamp) {
  return new Date(timestamp).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}
// Sem DTEND: a API não informa a duração do rolé, e o arquivo não deve inventar uma.
export function buildIcs(event, now = Date.now()) {
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//ChegaAi//Roles//PT",
    "CALSCALE:GREGORIAN",
    "BEGIN:VEVENT",
    `UID:${event.id}@chegaai`,
    `DTSTAMP:${icsUtc(now)}`,
    `DTSTART:${icsUtc(eventTimestamp(event))}`,
    `SUMMARY:${icsText(event.title)}`,
    `LOCATION:${icsText([event.place, event.cep && `CEP ${event.cep}`].filter(Boolean).join(" - "))}`,
    `DESCRIPTION:${icsText(event.description)}`,
    "END:VEVENT",
    "END:VCALENDAR",
  ];
  return lines.map(foldLine).join("\r\n") + "\r\n";
}
export function downloadIcs(event) {
  const blob = new Blob([buildIcs(event)], { type: "text/calendar;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `role-${categorySlug(event.title).slice(0, 40) || "chegaai"}.ics`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
