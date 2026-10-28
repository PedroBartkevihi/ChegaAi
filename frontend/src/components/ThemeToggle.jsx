import { MoonStar, Sun } from "lucide-react";
import { useTheme } from "../lib/tema";
// Chave de tema: bolinha com sol à esquerda (claro) ou lua à direita (escuro). A posição e o ícone vêm
// do CSS pelo data-theme do <html>, então já aparecem certos antes de o React montar.
export default function ThemeToggle() {
  const [dark, toggle] = useTheme();
  return (
    <button
      className="theme-toggle"
      type="button"
      role="switch"
      aria-checked={dark}
      aria-label="Tema escuro"
      title={dark ? "Usar o tema claro" : "Usar o tema escuro"}
      onClick={toggle}
    >
      <span className="theme-toggle-knob" aria-hidden="true">
        <Sun size={16} className="theme-toggle-sun" />
        <MoonStar size={16} className="theme-toggle-moon" />
      </span>
    </button>
  );
}
