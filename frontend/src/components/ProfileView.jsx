import { Award, LogOut, Trash2 } from "lucide-react";
import { useState } from "react";
import StackItem from "./StackItem";
import PendingRatings from "./PendingRatings";
import PasswordField from "./PasswordField";
import { formatEventDay, eventTimestamp, sortByDate } from "../lib/eventos";
const STATUS_LABELS = { CONFIRMADO: "Confirmado", FILA: "Lista de espera", AUSENTE: "Ausente" };
export default function ProfileView({ registrations, enrollments, currentUser, openEvent, onLogout, ratings, ratingsEventId, pending, sendRating, accountLoading, accountError, refreshAccount, deleteAccount }) {
  const now = Date.now();
  // Cada inscrição traz o resumo do rolé; uma saída recente some antes de a lista voltar da API.
  const myEnrollments = sortByDate(
    registrations
      .filter((item) => item.evento && enrollments[item.eventoId])
      .map((item) => item.evento)
  );
  const upcoming = myEnrollments.filter((event) => eventTimestamp(event) >= now);
  const past = myEnrollments.filter((event) => eventTimestamp(event) < now).reverse();
  const ambassador = currentUser.isAmbassador;
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
            <span>honra atual</span>
          </div>
          {ambassador && <span className="ambassador-tag"><Award size={14} aria-hidden="true" />Embaixador</span>}
          <p>{ambassador
            ? "Tag Embaixador ativa. Eventos criados por você ganham destaque e podem ter limite ampliado de vagas."
            : "Participe dos rolés e acompanhe sua reputação para se tornar Embaixador."
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
      <PendingRatings ratings={ratings} focusEventId={ratingsEventId} pending={pending} sendRating={sendRating} loading={accountLoading} error={accountError} retry={refreshAccount} />
      <DeleteAccount deleteAccount={deleteAccount} deleting={Boolean(pending.exclusaoConta)} />
    </section>
  );
}
// A senha confirma o pedido na API; antes, o App pede a confirmação final.
function DeleteAccount({ deleteAccount, deleting }) {
  const [password, setPassword] = useState("");
  return (
    <article className="profile-panel delete-account" aria-labelledby="excluir-conta">
      <h2 id="excluir-conta">Excluir minha conta</h2>
      <p>
        Seu nome, e-mail, CPF e data de nascimento são apagados, e a sessão termina. Os rolés futuros que você organiza
        são excluídos, e suas inscrições em rolés futuros são canceladas, sem penalidade. Presenças, avaliações e
        comentários continuam no histórico, com o nome "Conta excluída". Não dá para desfazer.
      </p>
      <form className="delete-account-form" onSubmit={(event) => { event.preventDefault(); if (!deleting) deleteAccount(password); }}>
        <PasswordField label="Sua senha" data-testid="input-senha-exclusao" autoComplete="current-password" value={password}
          onChange={(event) => setPassword(event.target.value)} required maxLength="72" />
        <button data-testid="btn-excluir-conta" className="danger-action" type="submit" aria-disabled={deleting || undefined}>
          <Trash2 size={16} aria-hidden="true" />
          {deleting ? "Excluindo..." : "Excluir minha conta"}
        </button>
      </form>
    </article>
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
