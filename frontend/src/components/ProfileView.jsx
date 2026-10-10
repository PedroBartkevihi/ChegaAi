import StackItem from "./StackItem";
import { isAmbassador, formatDate } from "../lib/eventos";
export default function ProfileView({ events, enrollments, currentUser }) {
  const myEnrollments = Object.entries(enrollments)
    .map(([eventId, status]) => ({ event: events.find((item) => item.id === eventId), status }))
    .filter((item) => item.event);
  return (
    <section className="view" aria-label="Perfil e honra">
      <div className="profile-grid">
        <article className="profile-panel">
          <span className="status-pill">Sua Reputação</span>
          <h2>{currentUser.fullName}</h2>
          <div className="big-honor">
            <strong data-testid="honor-value">{currentUser.honor.toFixed(1)}</strong>
            <span>reputação média</span>
          </div>
          <p>{isAmbassador(currentUser.honor) 
            ? "Tag Embaixador ativa. Eventos criados por você ganham destaque e podem ter limite ampliado de vagas."
            : "Participante ativo. Aumente sua reputação para se tornar Embaixador."
          }</p>
        </article>
        <article className="profile-panel">
          <span className="status-pill">Minhas inscrições</span>
          <h2>Confirmados e lista de espera</h2>
          <div className="pending-list">
            {myEnrollments.length ? (
              myEnrollments.map(({ event, status }) => <StackItem key={event.id} title={event.title} body={`${status} - ${formatDate(event.date)} às ${event.time}`} />)
            ) : (
              <StackItem title="Sem inscrições" body="Quando você participar de um rolé, ele aparecerá aqui." />
            )}
          </div>
        </article>
      </div>
    </section>
  );
}
