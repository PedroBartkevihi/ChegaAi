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
import { useEffect, useRef } from "react";
import EventCard from "./EventCard";
import Landscape from "./Landscape";
import { buildFilterSummary, categories, hasActiveFilters } from "../lib/eventos";
import { AVISO_SERVIDOR_ACORDANDO } from "../lib/useDemora";
// A API separa e ordena as seções; os anteriores só são pedidos quando a seção é aberta.
export default function FeedView({ feed, summary, loadingSlow, showPast, togglePast, loadMore, retryLoad, retryPast, filters, updateFilter, enrollments, enrollmentsReady, pending, currentUser, resetFilters, openEvent, toggleEnrollment, deleteEvent, onCreate }) {
  const { proximos, anteriores } = feed;
  const filtering = hasActiveFilters(filters);
  const pastCount = anteriores.state === "ready" || anteriores.items.length ? ` (${anteriores.total})` : "";
  const renderCard = (event) => (
    <EventCard
      key={event.id}
      event={event}
      enrollment={enrollments[event.id]}
      enrollmentsReady={enrollmentsReady}
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
        <Metric icon={<CalendarDays size={18} aria-hidden="true" />} label="Rolés abertos" value={summary?.active} />
        <Metric icon={<UsersRound size={18} aria-hidden="true" />} label="Na lista de espera" value={summary?.waitlist} />
        <Metric icon={<Star size={18} aria-hidden="true" />} label="Rolés de Embaixadores" value={summary?.ambassadors} />
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
      {proximos.state === "loading" && !proximos.items.length ? (
        <div className="empty-state" role="status">{loadingSlow ? AVISO_SERVIDOR_ACORDANDO : "Carregando rolés..."}</div>
      ) : proximos.state === "error" && !proximos.items.length ? (
        <div className="empty-state">
          <p>Não foi possível carregar os rolés. Verifique sua conexão e tente de novo.</p>
          <button className="secondary-action" type="button" onClick={retryLoad}>
            <RefreshCw size={16} aria-hidden="true" />
            Tentar de novo
          </button>
        </div>
      ) : proximos.items.length ? (
        <CardList section={proximos} renderCard={renderCard} loadMore={() => loadMore("proximos")} />
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
          <p>{summary?.total === 0 ? "Ainda não há rolés." : "Nenhum rolé aberto por enquanto."} Que tal organizar o próximo?</p>
          <button className="primary-action" type="button" onClick={onCreate}>
            <Plus size={16} aria-hidden="true" />
            Organizar um rolé
          </button>
        </div>
      )}
      {summary?.total !== 0 && (
        <section className="past-events" aria-label="Rolés anteriores e indisponíveis">
          <button
            className="past-toggle"
            type="button"
            aria-expanded={showPast}
            aria-controls="roles-anteriores"
            onClick={togglePast}
          >
            <ChevronDown size={18} aria-hidden="true" />
            {showPast ? "Ocultar" : "Mostrar"} rolés anteriores e indisponíveis{pastCount}
          </button>
          {showPast && (
            <div id="roles-anteriores">
              {anteriores.items.length ? (
                <CardList section={anteriores} renderCard={renderCard} loadMore={() => loadMore("anteriores")} />
              ) : anteriores.state === "error" ? (
                <div className="empty-state">
                  <p>Não foi possível carregar os rolés anteriores.</p>
                  <button className="secondary-action" type="button" onClick={retryPast}>
                    <RefreshCw size={16} aria-hidden="true" />
                    Tentar de novo
                  </button>
                </div>
              ) : anteriores.state === "ready" ? (
                <div className="empty-state">{filtering ? "Nenhum rolé anterior com os filtros atuais." : "Nenhum rolé anterior por enquanto."}</div>
              ) : (
                <div className="empty-state" role="status">Carregando rolés anteriores...</div>
              )}
            </div>
          )}
        </section>
      )}
    </section>
  );
}
// A grade de uma seção e o "Carregar mais". Na última página o botão some, e o foco vai para o primeiro rolé que chegou.
function CardList({ section, renderCard, loadMore }) {
  const gridRef = useRef(null);
  const focusFromRef = useRef(null);
  const loading = section.more === "loading";
  useEffect(() => {
    if (focusFromRef.current === null || loading) return;
    const from = focusFromRef.current;
    focusFromRef.current = null;
    if (!section.hasNext) gridRef.current?.querySelectorAll(".event-card")[from]?.querySelector("button")?.focus();
  }, [loading, section.hasNext]);
  return (
    <>
      <div ref={gridRef} className="event-grid" aria-busy={section.state === "loading" || undefined}>{section.items.map(renderCard)}</div>
      {(section.hasNext || section.pages > 1) && (
        <div className="load-more">
          <p className="muted" role="status">Mostrando {section.items.length} de {Math.max(section.total, section.items.length)} rolés</p>
          {section.hasNext && (
            <button
              className="secondary-action"
              type="button"
              aria-disabled={loading || section.state === "loading" || undefined}
              onClick={() => {
                focusFromRef.current = section.items.length;
                loadMore();
              }}
            >
              <ChevronDown size={16} aria-hidden="true" />
              {loading ? "Carregando..." : "Carregar mais rolés"}
            </button>
          )}
        </div>
      )}
    </>
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
            maxLength={100}
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
      <dd>{value ?? "–"}</dd>
    </div>
  );
}
