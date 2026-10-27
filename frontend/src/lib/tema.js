import { useEffect, useState } from "react";
import { DARK_QUERY, THEME_CHOICES, THEME_KEY } from "./tema-script";
function readChoice() {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    return THEME_CHOICES.includes(saved) ? saved : "system";
  } catch {
    return "system";
  }
}
// Aplica o tema no <html> e diz se ficou escuro.
function applyTheme(choice) {
  const dark = choice === "dark" || (choice === "system" && window.matchMedia(DARK_QUERY).matches);
  document.documentElement.setAttribute("data-theme", dark ? "dark" : "light");
  return dark;
}
// Sem escolha guardada, segue o sistema (inclusive se ele mudar com a página aberta). Ao usar a chave,
// a escolha (claro ou escuro) fica guardada e o sistema deixa de valer.
export function useTheme() {
  // Começa como o HTML do servidor (claro); o tema real, já aplicado pelo script do <head>, entra ao montar.
  const [choice, setChoice] = useState("system");
  const [dark, setDark] = useState(false);
  useEffect(() => {
    setChoice(readChoice());
    setDark(document.documentElement.getAttribute("data-theme") === "dark");
  }, []);
  useEffect(() => {
    if (choice !== "system") return;
    const media = window.matchMedia(DARK_QUERY);
    const follow = () => setDark(applyTheme("system"));
    media.addEventListener("change", follow);
    return () => media.removeEventListener("change", follow);
  }, [choice]);
  function toggle() {
    const next = dark ? "light" : "dark";
    setChoice(next);
    try {
      localStorage.setItem(THEME_KEY, next);
    } catch {
      // Sem armazenamento (aba anônima bloqueada): o tema vale só até recarregar.
    }
    setDark(applyTheme(next));
  }
  return [dark, toggle];
}
