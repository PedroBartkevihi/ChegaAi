import {
  CalendarDays,
  ChevronDown,
  Plus,
  RefreshCw,
  Search,
  Star,
  UsersRound,
  X,
} from "lucide-react";
import { useState } from "react";
import EventCard from "./EventCard";
import Landscape from "./Landscape";
import { buildFilterSummary, categories, hasActiveFilters, isUpcoming } from "../lib/eventos";
import { AVISO_SERVIDOR_ACORDANDO } from "../lib/useDemora";
export default function FeedView({ events, totalEvents, loadState, loadingSlow, retryLoad, metrics, filters, updateFilter, enrollments, pending, currentUser, resetFilters, openEvent, toggleEnrollment, deleteEvent, onCreate }) {
  const [showPast, setShowPast] = useState(false);
  const now = Date.now();
  const upcoming = events.filter((event) => isUpcoming(event, now));
  // Anteriores e indisponíveis: os mais recentes primeiro.
  const past = events.filter((event) => !isUpcoming(event, now)).reverse();
  const filtering = hasActiveFilters(filters);
  const renderCard = (event) => (
    <EventCard
      key={event.id}
      event={event}
      enrollment={enrollments[event.id]}
      pending={pending}
      currentUser={currentUser}
      openEvent={openEvent}
      toggleEnrollment={toggleEnrollment}
      deleteEvent={deleteEvent}
    />
  );
  return (
    <section className="view" aria-label="Feed de eventos">
      <div className="hero">
        <Landscape className="hero-art" />
        <div className="hero-copy">
          <p className="eyebrow">Rolés ao ar livre no Rio</p>
          <h1>O próximo rolé começa lá fora.</h1>
          <p>Futebol no Aterro, patinação na orla ou passeio com os pets: encontre companhia, confira a reputação de quem organiza e participe.</p>
          <button data-testid="btn-criar-role" className="primary-action" type="button" onClick={onCreate}>
            <Plus size={18} aria-hidden="true" />
            Criar rolé
          </button>
        </div>
      </div>
      <dl className="metric-row">
        <Metric icon={<CalendarDays size={18} aria-hidden="true" />} label="Rolés abertos" value={metrics.active} />
        <Metric icon={<UsersRound size={18} aria-hidden="true" />} label="Na lista de espera" value={metrics.waitlist} />
        <Metric icon={<Star size={18} aria-hidden="true" />} label="Rolés de Embaixadores" value={metrics.ambassadors} />
      </dl>
      <div className="section-heading">
        <div>
          <h2 id="roles-disponiveis" tabIndex={-1}>Rolés disponíveis</h2>
          <span>{buildFilterSummary(filters)} · em ordem de data</span>
        </div>
        <button className="secondary-action" type="button" onClick={resetFilters}>
          <X size={16} aria-hidden="true" />
          Limpar filtros
        </button>
      </div>
      <FeedFilters filters={filters} updateFilter={updateFilter} />
      {loadState === "loading" && !totalEvents ? (
        <div className="empty-state" role="status">{loadingSlow ? AVISO_SERVIDOR_ACORDANDO : "Carregando rolés..."}</div>
      ) : loadState === "error" && !totalEvents ? (
        <div className="empty-state">
          <p>Não foi possível carregar os rolés. Verifique sua conexão e tente de novo.</p>
          <button className="secondary-action" type="button" onClick={retryLoad}>
            <RefreshCw size={16} aria-hidden="true" />
            Tentar de novo
          </button>
        </div>
      ) : upcoming.length ? (
        <div className="event-grid">{upcoming.map(renderCard)}</div>
      ) : filtering ? (
        <div className="empty-state">
          <p>Nenhum rolé aberto com os filtros atuais.</p>
          <button className="secondary-action" type="button" onClick={resetFilters}>
            <X size={16} aria-hidden="true" />
            Limpar filtros
          </button>
        </div>
      ) : (
        <div className="empty-state">
          <p>{totalEvents ? "Nenhum rolé aberto por enquanto." : "Ainda não há rolés."} Que tal organizar o próximo?</p>
          <button className="primary-action" type="button" onClick={onCreate}>
            <Plus size={16} aria-hidden="true" />
            Organizar um rolé
          </button>
        </div>
      )}
      {past.length > 0 && (
        <section className="past-events" aria-label="Rolés anteriores e indisponíveis">
          <button
            className="past-toggle"
            type="button"
            aria-expanded={showPast}
            aria-controls="roles-anteriores"
            onClick={() => setShowPast((current) => !current)}
          >
            <ChevronDown size={18} aria-hidden="true" />
            {showPast ? "Ocultar" : "Mostrar"} rolés anteriores e indisponíveis ({past.length})
          </button>
          {showPast && <div id="roles-anteriores" className="event-grid">{past.map(renderCard)}</div>}
        </section>
      )}
    </section>
  );
}
function FeedFilters({ filters, updateFilter }) {
  return (
    <div className="filters" role="group" aria-label="Filtros de eventos">
      <label className="filter-search">
        Buscar rolé
        <span className="input-with-icon">
          <Search size={16} aria-hidden="true" />
          <input
            data-testid="search-input"
            value={filters.search}
            type="search"
            placeholder="Buscar rolés, lugares ou atividades..."
            onChange={(event) => updateFilter("search", event.target.value)}
          />
        </span>
      </label>
      <SelectFilter label="Categoria" value={filters.category} onChange={(value) => updateFilter("category", value)}>
        <option value="todos">Todas</option>
        {categories.map((category) => <option key={category} value={category}>{category}</option>)}
      </SelectFilter>
      <SelectFilter label="Honra do organizador" value={filters.honor} onChange={(value) => updateFilter("honor", value)}>
        <option value="0">Qualquer honra</option>
        <option value="4">4.0+</option>
        <option value="ambassador">Embaixadores</option>
      </SelectFilter>
    </div>
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
function Metric({ icon, label, value }) {
  return (
    <div>
      <dt>{icon}{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
