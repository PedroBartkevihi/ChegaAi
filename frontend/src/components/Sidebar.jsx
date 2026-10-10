import { Search } from "lucide-react";
import { categories, isAmbassador } from "../lib/eventos";
export default function Sidebar({ view, filters, setView, updateFilter, currentUser, onLogout }) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">Aí</span>
        <div>
          <strong>ChegaAí</strong>
          <small>Companhia para sair de casa</small>
        </div>
      </div>
      <section className="profile-summary" aria-label="Resumo do perfil">
        <div>
          <span className="avatar" aria-hidden="true">{currentUser.name[0]}</span>
          <div>
            <strong>{currentUser.name}</strong>
            <small>{isAmbassador(currentUser.honor) ? "Embaixador" : "Participante"}</small>
          </div>
        </div>
        <div className="honor-meter">
          <span>Reputação</span>
          <strong>{currentUser.honor.toFixed(1)}</strong>
        </div>
      </section>
      <nav className="nav-tabs" aria-label="Navegação principal">
        {[
          ["feed", "Feed"],
          ["create", "Criar rolé"],
          ["profile", "Meu perfil"],
          ...(currentUser.isModerator ? [["moderation", "Moderação"]] : []),
        ].map(([key, label]) => (
          <button 
            className={`nav-tab ${view === key ? "active" : ""}`} 
            key={key} 
            type="button" 
            onClick={() => setView(key)}
          >
            {label}
          </button>
        ))}
        <button className="nav-tab" type="button" onClick={onLogout} style={{ marginTop: "auto", opacity: 0.7 }}>
          Sair
        </button>
      </nav>
      <section className="filters" aria-label="Filtros de eventos">
        <label>
          Buscar rolé
          <span className="input-with-icon">
            <Search size={16} />
            <input 
              data-testid="search-input"
              value={filters.search} 
              type="search" 
              placeholder="Buscar eventos..." 
              onChange={(event) => updateFilter("search", event.target.value)} 
            />
          </span>
        </label>
        <SelectFilter label="Categoria" value={filters.category} onChange={(value) => updateFilter("category", value)}>
          <option value="todos">Todas</option>
          {categories.map((category) => <option key={category} value={category}>{category}</option>)}
        </SelectFilter>
        <SelectFilter label="Distância" value={filters.distance} onChange={(value) => updateFilter("distance", value)}>
          <option value="todos">Qualquer distância</option>
          <option value="5">Até 5 km</option>
          <option value="15">Até 15 km</option>
          <option value="30">Até 30 km</option>
        </SelectFilter>
        <SelectFilter label="Honra do organizador" value={filters.honor} onChange={(value) => updateFilter("honor", value)}>
          <option value="0">Qualquer honra</option>
          <option value="4">4.0+</option>
          <option value="4.8">Embaixadores</option>
        </SelectFilter>
      </section>
    </aside>
  );
}
function SelectFilter({ label, value, onChange, children }) {
  return (
    <label>
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {children}
      </select>
    </label>
  );
}
