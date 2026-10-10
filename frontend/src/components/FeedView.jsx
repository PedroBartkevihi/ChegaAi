import {
  CalendarDays,
  Star,
  UsersRound,
  X,
} from "lucide-react";
import EventCard from "./EventCard";
import { buildFilterSummary } from "../lib/eventos";
export default function FeedView({ events, metrics, filters, enrollments, currentUser, resetFilters, openEvent, toggleEnrollment, deleteEvent }) {
  return (
    <section className="view" aria-label="Feed de eventos">
      <div className="mission-panel">
        <div>
          <span className="status-pill">Sobre o ChegaAí</span>
          <h2>Conectando pessoas para atividades presenciais com segurança.</h2>
          <p>Nossa plataforma prioriza organizadores bem avaliados, gerencia participantes por lista de espera e centraliza os combinados do grupo.</p>
        </div>
        <div className="map-sketch" aria-hidden="true">
          <span className="route" />
          <span className="pin pin-a" />
          <span className="pin pin-b" />
          <span className="pin pin-c" />
          <strong>RJ</strong>
        </div>
      </div>
      <div className="metric-row">
        <Metric icon={<CalendarDays size={18} />} label="Eventos ativos" value={metrics.active} />
        <Metric icon={<UsersRound size={18} />} label="Na lista de espera" value={metrics.waitlist} />
        <Metric icon={<Star size={18} />} label="Embaixadores" value={metrics.ambassadors} />
      </div>
      <div className="section-heading">
        <div>
          <h2>Rolés disponíveis</h2>
          <span>{buildFilterSummary(filters)}</span>
        </div>
        <button className="secondary-action" type="button" onClick={resetFilters}>
          <X size={16} />
          Limpar filtros
        </button>
      </div>
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
function Metric({ icon, label, value }) {
  return (
    <article>
      <span>{icon}{label}</span>
      <strong>{value}</strong>
    </article>
  );
}
