import { CalendarPlus, Compass, LogOut, ShieldCheck, UserRound } from "lucide-react";
import { isAmbassador } from "../lib/eventos";
import Brand from "./Brand";
export default function SiteHeader({ view, setView, currentUser, onLogout }) {
  const tabs = [
    ["feed", "Feed", Compass],
    ["create", "Criar rolé", CalendarPlus],
    ["profile", "Meu perfil", UserRound],
    ...(currentUser.isModerator ? [["moderation", "Moderação", ShieldCheck]] : []),
  ];
  return (
    <header className="site-header">
      <div className="site-header-inner">
        <Brand />
        <nav className="nav-tabs" aria-label="Navegação principal">
          {tabs.map(([key, label, Icon]) => (
            <button
              className={`nav-tab ${view === key ? "active" : ""}`}
              key={key}
              type="button"
              aria-current={view === key ? "page" : undefined}
              onClick={() => setView(key)}
            >
              <Icon size={17} aria-hidden="true" />
              {label}
            </button>
          ))}
        </nav>
        <section className="user-chip" aria-label="Resumo do perfil">
          <span className="avatar" aria-hidden="true">{currentUser.name[0]}</span>
          <div>
            <strong>{currentUser.name}</strong>
            <small>
              {isAmbassador(currentUser.honor) ? "Embaixador" : "Participante"} · reputação {currentUser.honor.toFixed(1)}
            </small>
          </div>
          <button className="ghost-action" type="button" onClick={onLogout}>
            <LogOut size={16} aria-hidden="true" />
            Sair
          </button>
        </section>
      </div>
    </header>
  );
}
