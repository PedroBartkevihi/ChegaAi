import { Award, LogOut } from "lucide-react";
import StackItem from "./StackItem";
import { isAmbassador, formatEventDay, eventTimestamp, sortByDate } from "../lib/eventos";
const STATUS_LABELS = { CONFIRMADO: "Confirmado", FILA: "Lista de espera" };
export default function ProfileView({ events, enrollments, currentUser, openEvent, onLogout }) {
  const now = Date.now();
  const myEnrollments = sortByDate(
    Object.keys(enrollments)
      .map((eventId) => events.find((item) => item.id === eventId))
      .filter(Boolean)
  );
  const upcoming = myEnrollments.filter((event) => eventTimestamp(event) >= now);
  const past = myEnrollments.filter((event) => eventTimestamp(event) < now).reverse();
  const ambassador = isAmbassador(currentUser.honor);
  return (
    <section className="view" aria-label="Perfil e honra">
      <div className="page-heading">
        <p className="eyebrow">Meu perfil</p>
        <h1>{currentUser.fullName}</h1>
        {/* No celular a barra lateral vira barra de abas, sem o "Sair": ele fica aqui. */}
        <button className="ghost-action mobile-only" type="button" onClick={onLogout}>
          <LogOut size={16} aria-hidden="true" />
          Sair da conta
        </button>
      </div>
      <div className="profile-grid">
        <article className="profile-panel honor-panel">
          <span className="status-pill">Sua reputação</span>
          <div className="big-honor">
            <strong data-testid="honor-value">{currentUser.honor.toFixed(1)}</strong>
            <span>reputação média</span>
          </div>
          {ambassador && <span className="ambassador-tag"><Award size={14} aria-hidden="true" />Embaixador</span>}
          <p>{ambassador
            ? "Tag Embaixador ativa. Eventos criados por você ganham destaque e podem ter limite ampliado de vagas."
            : "Participante ativo. Aumente sua reputação para se tornar Embaixador."
          }</p>
        </article>
        <article className="profile-panel">
          <span className="status-pill">Minhas inscrições</span>
          <h2>Confirmados e lista de espera</h2>
          {myEnrollments.length ? (
            <>
              <EnrollmentList title="Próximos" events={upcoming} enrollments={enrollments} openEvent={openEvent} empty="Nenhum rolé marcado daqui para a frente." />
              {past.length > 0 && <EnrollmentList title="Anteriores" events={past} enrollments={enrollments} openEvent={openEvent} />}
            </>
          ) : (
            <StackItem title="Sem inscrições" body="Quando você participar de um rolé, ele aparecerá aqui." />
          )}
        </article>
      </div>
    </section>
  );
}
function EnrollmentList({ title, events, enrollments, openEvent, empty }) {
  return (
    <section className="enrollment-list" aria-label={title}>
      <h3>{title}</h3>
      {events.length ? (
        <ul className="pending-list">
          {events.map((event) => (
            <li key={event.id} className="stack-item enrollment-item">
              <div>
                <strong>{event.title}</strong>
                <p>
                  {STATUS_LABELS[enrollments[event.id]] || enrollments[event.id]} ·{" "}
                  <time dateTime={`${event.date}T${event.time}`}>{formatEventDay(event.date)} · {event.time}</time>
                </p>
              </div>
              <button className="secondary-action" type="button" onClick={() => openEvent(event.id)}>
                Ver detalhes<span className="sr-only"> de {event.title}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted">{empty}</p>
      )}
    </section>
  );
}
