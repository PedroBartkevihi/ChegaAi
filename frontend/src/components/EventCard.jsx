import { Award, CalendarDays, MapPin, Star, Trash2, UsersRound } from "lucide-react";
import CategoryIcon from "./CategoryIcon";
import { getEnrollmentActionLabel, formatEventDay, categorySlug, eventTimestamp } from "../lib/eventos";
export default function EventCard({ event, enrollment, enrollmentsReady = true, pending = {}, currentUser, openEvent, toggleEnrollment, deleteEvent }) {
  const seats = Math.max(event.capacity - event.confirmedCount, 0);
  const percent = Math.min((event.confirmedCount / event.capacity) * 100, 100);
  const featured = event.organizer.isAmbassador;
  const isOrganizer = event.organizer.id === currentUser?.id;
  const locked = !enrollmentsReady || isOrganizer || event.status !== "ABERTO" || eventTimestamp(event) <= Date.now();
  const canManage = currentUser?.isModerator || event.organizer.id === currentUser?.id;
  const slug = categorySlug(event.category);
  const enrolling = Boolean(pending[`inscricao:${event.id}`]);
  const deleting = Boolean(pending[`exclusao:${event.id}`]);
  return (
    <article data-testid={`event-card-${event.id}`} className={`event-card cat-${slug} ${featured ? "featured" : ""}`} aria-busy={enrolling || deleting}>
      <div className="event-card-band">
        <span className="event-type"><CategoryIcon slug={slug} size={15} />{event.category}</span>
        <EnrollmentBadge event={event} enrollment={enrollment} />
        <CategoryIcon slug={slug} size={74} className="event-card-band-art" />
      </div>
      <div className="event-card-body">
        <h3>{event.title}</h3>
        <ul className="event-meta">
          <li>
            <CalendarDays size={16} aria-hidden="true" />
            <span><time dateTime={`${event.date}T${event.time}`}>{formatEventDay(event.date)} · {event.time}</time></span>
          </li>
          <li><MapPin size={16} aria-hidden="true" /><span>{event.place}{event.distance > 0 ? ` - ${event.distance} km` : ""}</span></li>
          {event.confirmedCount >= event.capacity ? (
            <li>
              <span className="waitlist-pill">
                <UsersRound size={14} aria-hidden="true" /> Lista de espera ativa: {event.waitlistCount} {event.waitlistCount === 1 ? "pessoa" : "pessoas"} na fila
              </span>
            </li>
          ) : (
            <li><UsersRound size={16} aria-hidden="true" /><span>{event.confirmedCount}/{event.capacity} confirmados · {seats} vaga(s) restante(s)</span></li>
          )}
        </ul>
        <div className="progress-track" aria-hidden="true">
          <span className="progress-fill" style={{ width: `${percent}%` }} />
        </div>
        <p className="event-description">{event.description}</p>
        <div className="organizer-line">
          <span className="avatar small" aria-hidden="true">{event.organizer.name[0]}</span>
          <span>Organizador: <strong>{event.organizer.name}</strong></span>
          <span className="organizer-rating">
            <Star size={14} aria-hidden="true" />
            Reputação {event.organizer.honor.toFixed(1)}
          </span>
          {featured && <span className="ambassador-tag"><Award size={14} aria-hidden="true" />Embaixador</span>}
        </div>
        <div className="card-actions">
          <button className="secondary-action" type="button" onClick={() => openEvent(event.id)}>Detalhes</button>
          <button className={locked ? "secondary-action" : "primary-action"} type="button" disabled={locked} aria-disabled={enrolling || undefined} onClick={() => toggleEnrollment(event.id)}>
            {enrolling ? "Enviando..." : !enrollmentsReady ? "Carregando inscrição..." : isOrganizer ? "Você organiza este rolé" : getEnrollmentActionLabel(event, enrollment)}
          </button>
          {canManage && event.status !== "CONCLUIDO" && (
            <button className="danger-action" type="button" aria-disabled={deleting || undefined} onClick={() => deleteEvent(event.id)}>
              <Trash2 size={16} aria-hidden="true" />
              {deleting ? "Excluindo..." : "Excluir"}
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
function EnrollmentBadge({ event, enrollment }) {
  if (event.status === "TRAVADO") return <span className="enrollment-badge locked">Suspenso</span>;
  if (event.status === "CANCELADO") return <span className="enrollment-badge locked">Cancelado</span>;
  if (event.status === "CONCLUIDO") return <span className="enrollment-badge done">Concluído</span>;
  if (enrollment === "CONFIRMADO") return <span className="enrollment-badge confirmed">Confirmado</span>;
  if (enrollment === "FILA") return <span className="enrollment-badge waiting">Lista de Espera</span>;
  if (event.confirmedCount >= event.capacity) return <span className="enrollment-badge waiting">Esgotado</span>;
  return <span className="enrollment-badge open">Vagas Abertas</span>;
}
