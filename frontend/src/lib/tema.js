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
function applyTheme(choice) {
  const dark = choice === "dark" || (choice === "system" && window.matchMedia(DARK_QUERY).matches);
  document.documentElement.setAttribute("data-theme", dark ? "dark" : "light");
}
export function useThemeChoice() {
  // Começa em "system", como o HTML do servidor; a escolha guardada entra depois de montar.
  const [choice, setChoice] = useState("system");
  useEffect(() => {
    setChoice(readChoice());
  }, []);
  useEffect(() => {
    if (choice !== "system") return;
    const media = window.matchMedia(DARK_QUERY);
    const follow = () => applyTheme("system");
    media.addEventListener("change", follow);
    return () => media.removeEventListener("change", follow);
  }, [choice]);
  function changeChoice(next) {
    setChoice(next);
    try {
      localStorage.setItem(THEME_KEY, next);
    } catch {
      // Sem armazenamento (aba anônima bloqueada): o tema vale só até recarregar.
    }
    applyTheme(next);
  }
  return [choice, changeChoice];
}
