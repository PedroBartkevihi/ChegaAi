import { Award } from "lucide-react";
import StackItem from "./StackItem";
import { isAmbassador, formatDate } from "../lib/eventos";
const STATUS_LABELS = { CONFIRMADO: "Confirmado", FILA: "Lista de espera" };
export default function ProfileView({ events, enrollments, currentUser }) {
  const myEnrollments = Object.entries(enrollments)
    .map(([eventId, status]) => ({ event: events.find((item) => item.id === eventId), status }))
    .filter((item) => item.event);
  const ambassador = isAmbassador(currentUser.honor);
  return (
    <section className="view" aria-label="Perfil e honra">
      <div className="page-heading">
        <p className="eyebrow">Meu perfil</p>
        <h1>{currentUser.fullName}</h1>
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
          <div className="pending-list">
            {myEnrollments.length ? (
              myEnrollments.map(({ event, status }) => (
                <StackItem key={event.id} title={event.title} body={`${STATUS_LABELS[status] || status} - ${formatDate(event.date)} às ${event.time}`} />
              ))
            ) : (
              <StackItem title="Sem inscrições" body="Quando você participar de um rolé, ele aparecerá aqui." />
            )}
          </div>
        </article>
      </div>
    </section>
  );
}
