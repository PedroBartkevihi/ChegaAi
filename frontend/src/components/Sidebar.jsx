import { CalendarPlus, Compass, LogOut, ShieldCheck, UserRound } from "lucide-react";
import Brand from "./Brand";
// Barra lateral no computador; no celular, o CSS deixa só a navegação, como barra de abas fixa embaixo
// (o "Sair" passa para o Meu perfil). A navegação é uma só no DOM, para não duplicar os botões.
export default function Sidebar({ view, setView, currentUser, onLogout }) {
  const tabs = [
    ["feed", "Feed", Compass],
    ["create", "Criar rolé", CalendarPlus],
    ["profile", "Meu perfil", UserRound],
    ...(currentUser.isModerator ? [["moderation", "Moderação", ShieldCheck]] : []),
  ];
  return (
    <aside className="sidebar">
      <Brand />
      <section className="sidebar-profile" aria-label="Resumo do perfil">
        <span className="avatar" aria-hidden="true">{currentUser.name[0]}</span>
        <div>
          <strong>{currentUser.name}</strong>
          <small>{currentUser.isAmbassador ? "Embaixador" : "Participante"}</small>
        </div>
        <div className="sidebar-honor">
          <span>Reputação</span>
          <strong>{currentUser.honor.toFixed(1)}</strong>
        </div>
      </section>
      <nav className="nav-tabs" aria-label="Navegação principal">
        {tabs.map(([key, label, Icon]) => (
          <button
            className={`nav-tab ${view === key ? "active" : ""}`}
            key={key}
            type="button"
            aria-current={view === key ? "page" : undefined}
            onClick={() => setView(key)}
          >
            <Icon size={18} aria-hidden="true" />
            {label}
          </button>
        ))}
      </nav>
      <button className="ghost-action sidebar-logout" type="button" onClick={onLogout}>
        <LogOut size={16} aria-hidden="true" />
        Sair
      </button>
    </aside>
  );
}
