import {
  CalendarDays,
  Plus,
  Search,
  Star,
  UsersRound,
  X,
} from "lucide-react";
import EventCard from "./EventCard";
import Landscape from "./Landscape";
import { buildFilterSummary, categories } from "../lib/eventos";
export default function FeedView({ events, metrics, filters, updateFilter, enrollments, currentUser, resetFilters, openEvent, toggleEnrollment, deleteEvent, onCreate }) {
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
          <span>{buildFilterSummary(filters)}</span>
        </div>
        <button className="secondary-action" type="button" onClick={resetFilters}>
          <X size={16} aria-hidden="true" />
          Limpar filtros
        </button>
      </div>
      <FeedFilters filters={filters} updateFilter={updateFilter} />
      <div className="event-grid">
        {events.length ? (
          events.map((event) => (
            <EventCard
              key={event.id}
              event={event}
              enrollment={enrollments[event.id]}
              currentUser={currentUser}
              openEvent={openEvent}
              toggleEnrollment={toggleEnrollment}
              deleteEvent={deleteEvent}
            />
          ))
        ) : (
          <div className="empty-state">Nenhum rolé encontrado com os filtros atuais.</div>
        )}
      </div>
    </section>
  );
}
function FeedFilters({ filters, updateFilter }) {
  // A categoria precisa continuar sendo o primeiro select da página (os testes E2E contam com isso).
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
