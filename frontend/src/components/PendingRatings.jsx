import { useEffect, useId, useRef, useState } from "react";
import { formatEventDateLong } from "../lib/eventos";

export default function PendingRatings({ ratings, focusEventId, pending, sendRating, loading, error, retry }) {
  const focusedEventRef = useRef(null);
  useEffect(() => {
    if (!focusEventId || focusedEventRef.current === focusEventId) return;
    const heading = document.getElementById(`avaliacoes-${focusEventId}`);
    if (!heading) return;
    focusedEventRef.current = focusEventId;
    heading.focus();
    heading.scrollIntoView({ block: "center" });
  }, [focusEventId, ratings.eventos]);
  return (
    <section className="profile-panel ratings-panel" aria-label="Avaliações pendentes">
      <div className="section-heading compact-heading"><h2>Avaliações pendentes</h2><button className="ghost-action" type="button" disabled={loading} onClick={retry}>Atualizar</button></div>
      <p>Avalie os outros participantes dos rolés em que você compareceu para se inscrever em novos encontros.</p>
      {loading && <p role="status">Atualizando suas avaliações...</p>}
      {error && <div className="empty-state"><p>Não foi possível atualizar todas as informações. Tente novamente para conferir suas pendências.</p><button className="secondary-action" type="button" onClick={retry}>Tentar novamente</button></div>}
      {!ratings.podeAvaliar && ratings.eventos.length > 0 && <p className="field-hint">Você pode consultar as pendências, mas sua conta não pode enviar avaliações no momento.</p>}
      {ratings.eventos.length ? ratings.eventos.map((event) => (
        <section className="rating-event" key={event.eventoId} aria-labelledby={`avaliacoes-${event.eventoId}`}>
          <h3 id={`avaliacoes-${event.eventoId}`} tabIndex={-1}>{event.titulo}</h3>
          <p className="muted">{formatEventDateLong(event.data)} · {event.hora}</p>
          <div className="rating-list">{event.participantes.map((person) => <RatingForm key={person.id} person={person} eventId={event.eventoId} canRate={ratings.podeAvaliar} submitting={Boolean(pending[`avaliacao:${event.eventoId}:${person.id}`])} sendRating={sendRating} />)}</div>
        </section>
      )) : !loading && !error && <div className="empty-state">Você está em dia. Nenhuma avaliação pendente.</div>}
    </section>
  );
}

function RatingForm({ person, eventId, canRate, submitting, sendRating }) {
  const [note, setNote] = useState("");
  const [comment, setComment] = useState("");
  const formId = useId();
  async function submit(event) {
    event.preventDefault();
    if (!canRate || submitting) return;
    if (await sendRating(eventId, person.id, Number(note), comment)) {
      setNote("");
      setComment("");
    }
  }
  return (
    <form className="rating-form" onSubmit={submit} aria-label={`Avaliar ${person.nome}`}>
      <h4>{person.nome}</h4>
      <label htmlFor={`${formId}-nota`}>Nota de 1 a 5</label>
      <select id={`${formId}-nota`} value={note} required disabled={!canRate || submitting} onChange={(event) => setNote(event.target.value)}>
        <option value="" disabled>Selecione a nota</option>{[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value}</option>)}
      </select>
      <label htmlFor={`${formId}-comentario`}>Comentário (opcional)</label>
      <textarea id={`${formId}-comentario`} value={comment} disabled={!canRate || submitting} rows="3" maxLength="180" onChange={(event) => setComment(event.target.value)} />
      <button className="secondary-action" type="submit" disabled={!canRate || submitting}>{submitting ? "Enviando..." : "Enviar avaliação"}</button>
    </form>
  );
}
