import { SunMoon } from "lucide-react";
import { useThemeChoice } from "../lib/tema";
export default function ThemeSelect() {
  const [choice, setChoice] = useThemeChoice();
  return (
    <label className="theme-select">
      <SunMoon size={16} aria-hidden="true" />
      <span className="sr-only">Tema</span>
      <select value={choice} onChange={(event) => setChoice(event.target.value)}>
        <option value="system">Sistema</option>
        <option value="light">Claro</option>
        <option value="dark">Escuro</option>
      </select>
    </label>
  );
}
