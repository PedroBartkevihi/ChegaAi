import {
  Flag,
  MapPin,
  Trash2,
  X,
} from "lucide-react";
import { useState } from "react";
import { getEnrollmentActionLabel, formatDate } from "../lib/eventos";
export default function EventModal({ event, enrollment, close, toggleEnrollment, addComment, deleteEvent, reportEvent, currentUser }) {
  const [comment, setComment] = useState("");
  const canComment = enrollment === "CONFIRMADO" || enrollment === "FILA";
  const canManage = currentUser?.isModerator || event.organizer.id === currentUser?.id;
  function submitComment(formEvent) {
    formEvent.preventDefault();
    addComment(event.id, comment);
    setComment("");
  }
  return (
    <div className="modal-backdrop" role="presentation">
      <section className="event-dialog" role="dialog" aria-modal="true" aria-label={event.title}>
        <button className="icon-button" type="button" aria-label="Fechar" onClick={close}><X size={18} /></button>
        <div className="dialog-body">
          <div className="dialog-title">
            <span className="event-type">{event.category}</span>
            <h2>{event.title}</h2>
          </div>
          <p>{event.description}</p>
          <div className="dialog-stats">
            <div><strong>{formatDate(event.date)}</strong><span>{event.time}</span></div>
            <div><strong>{event.confirmed.length}/{event.capacity}</strong><span>confirmados</span></div>
            <div><strong>{event.waitlist.length}</strong><span>na lista de espera</span></div>
          </div>
          <div className="organizer-line">
            <span><MapPin size={15} /> {event.place} - CEP {event.cep || "não informado"}</span>
            <span>Reputação {event.organizer.honor.toFixed(1)}</span>
          </div>
          <div className="card-actions">
            <button className="primary-action" type="button" disabled={event.status !== "ABERTO"} onClick={() => toggleEnrollment(event.id)}>
              {getEnrollmentActionLabel(event, enrollment)}
            </button>
            <button className="secondary-action" type="button" onClick={() => reportEvent(event.id)}>
              <Flag size={16} />
              Denunciar
            </button>
            {canManage && (
              <button className="danger-action" type="button" onClick={() => deleteEvent(event.id)}>
                <Trash2 size={16} />
                Excluir
              </button>
            )}
          </div>
          <section className="comments-box">
            <h3>Comentários do rolé</h3>
            {event.comments.length ? (
              event.comments.map((item, index) => (
                <article className="comment-item" key={`${item.author}-${item.time}-${index}`}>
                  <header><strong>{item.author}</strong><span className="muted">{item.time}</span></header>
                  <p>{item.text}</p>
                </article>
              ))
            ) : (
              <div className="empty-state">Sem comentários ainda.</div>
            )}
            {canComment ? (
              <form className="comment-form" onSubmit={submitComment}>
                <label>
                  Novo comentário
                  <textarea value={comment} onChange={(event) => setComment(event.target.value)} required maxLength="180" rows="3" placeholder="Escreva seu comentário para o grupo..." />
                </label>
                <button className="secondary-action" type="submit">Enviar comentário</button>
              </form>
            ) : (
              <div className="empty-state">Apenas participantes confirmados ou na lista de espera podem comentar.</div>
            )}
          </section>
        </div>
      </section>
    </div>
  );
}
