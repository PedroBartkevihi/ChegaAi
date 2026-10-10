import { Trash2, UsersRound } from "lucide-react";
import { getEnrollmentActionLabel, isAmbassador, formatDate } from "../lib/eventos";
export default function EventCard({ event, enrollment, currentUser, openEvent, toggleEnrollment, deleteEvent }) {
  const seats = Math.max(event.capacity - event.confirmed.length, 0);
  const percent = Math.min((event.confirmed.length / event.capacity) * 100, 100);
  const featured = isAmbassador(event.organizer.honor);
  const locked = event.status !== "ABERTO";
  const canManage = currentUser?.isModerator || event.organizer.id === currentUser?.id;
  return (
    <article data-testid={`event-card-${event.id}`} className={`event-card ${featured ? "featured" : ""}`}>
      <div className="event-card-header">
        <div>
          <span className="event-type">{event.category}</span>
          <h3>{event.title}</h3>
        </div>
        <EnrollmentBadge event={event} enrollment={enrollment} />
      </div>
      <div className="event-meta">
        <span>{formatDate(event.date)} às {event.time}</span>
        <span>{event.place}{event.distance > 0 ? ` - ${event.distance} km` : ""}</span>
        {event.confirmed.length >= event.capacity ? (
          <div style={{ marginTop: "6px" }}>
            <span style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              color: "var(--amber)",
              background: "rgba(217, 146, 32, 0.1)",
              padding: "4px 10px",
              borderRadius: "6px",
              fontSize: "0.82rem",
              fontWeight: "600"
            }}>
              <UsersRound size={14} /> Lista de espera ativa: {event.waitlist.length} {event.waitlist.length === 1 ? "pessoa" : "pessoas"} na fila
            </span>
          </div>
        ) : (
          <span>{event.confirmed.length}/{event.capacity} confirmados · {seats} vaga(s) restante(s)</span>
        )}
      </div>
      <div className="progress-track" aria-hidden="true">
        <span className="progress-fill" style={{ width: `${percent}%` }} />
      </div>
      <p>{event.description}</p>
      <div className="organizer-line">
        <span>Organizador: <strong>{event.organizer.name}</strong></span>
        <span>Reputação {event.organizer.honor.toFixed(1)}{featured ? " · Embaixador" : ""}</span>
      </div>
      <div className="card-actions">
        <button className="secondary-action" type="button" onClick={() => openEvent(event.id)}>Detalhes</button>
        <button className={locked ? "secondary-action" : "primary-action"} type="button" disabled={locked} onClick={() => toggleEnrollment(event.id)}>
          {getEnrollmentActionLabel(event, enrollment)}
        </button>
        {canManage && (
          <button className="danger-action" type="button" onClick={() => deleteEvent(event.id)}>
            <Trash2 size={16} />
            Excluir
          </button>
        )}
      </div>
    </article>
  );
}
function EnrollmentBadge({ event, enrollment }) {
  if (event.status === "TRAVADO") return <span className="enrollment-badge locked">Suspenso</span>;
  if (event.status === "CANCELADO") return <span className="enrollment-badge locked">Cancelado</span>;
  if (enrollment === "CONFIRMADO") return <span className="enrollment-badge confirmed">Confirmado</span>;
  if (enrollment === "FILA") return <span className="enrollment-badge waiting">Lista de Espera</span>;
  if (event.confirmed.length >= event.capacity) return <span className="enrollment-badge waiting">Esgotado</span>;
  return <span className="enrollment-badge confirmed">Vagas Abertas</span>;
}
