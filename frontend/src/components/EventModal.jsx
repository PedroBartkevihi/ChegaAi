import {
  CalendarDays,
  CalendarPlus,
  ExternalLink,
  Flag,
  MapPin,
  Star,
  Trash2,
  UsersRound,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import CategoryIcon from "./CategoryIcon";
import Notice from "./Notice";
import { getEnrollmentActionLabel, formatEventDateLong, categorySlug } from "../lib/eventos";
import { downloadIcs, mapUrl } from "../lib/agenda";
const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
export default function EventModal({ event, enrollment, pending = {}, close, notice, onCloseNotice, toggleEnrollment, addComment, deleteEvent, reportEvent, currentUser }) {
  const [comment, setComment] = useState("");
  const dialogRef = useRef(null);
  const closeRef = useRef(null);
  // O App recria a função close a cada renderização; guardá-la num ref evita refazer o efeito
  // abaixo (e devolver o foco ao botão de fechar) toda vez que a lista de eventos atualiza.
  const onCloseRef = useRef(close);
  onCloseRef.current = close;
  const canComment = enrollment === "CONFIRMADO" || enrollment === "FILA";
  const canManage = currentUser?.isModerator || event.organizer.id === currentUser?.id;
  const slug = categorySlug(event.category);
  const enrolling = Boolean(pending[`inscricao:${event.id}`]);
  const deleting = Boolean(pending[`exclusao:${event.id}`]);
  const commenting = Boolean(pending[`comentario:${event.id}`]);
  // Ao abrir, o foco vai para o botão de fechar; Esc fecha; Tab circula só dentro do diálogo;
  // ao fechar, o foco volta para o botão que abriu o diálogo ou, se ele sumiu (rolé excluído,
  // formulário de criação desmontado), para o título da lista de rolés.
  useEffect(() => {
    const opener = document.activeElement;
    closeRef.current?.focus();
    function handleKeyDown(keyEvent) {
      if (keyEvent.key === "Escape") {
        keyEvent.preventDefault();
        onCloseRef.current();
        return;
      }
      if (keyEvent.key !== "Tab" || !dialogRef.current) return;
      const focusable = [...dialogRef.current.querySelectorAll(FOCUSABLE)];
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (keyEvent.shiftKey && document.activeElement === first) {
        keyEvent.preventDefault();
        last.focus();
      } else if (!keyEvent.shiftKey && document.activeElement === last) {
        keyEvent.preventDefault();
        first.focus();
      }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      if (opener instanceof HTMLElement && opener.isConnected && opener !== document.body) opener.focus();
      else document.getElementById("roles-disponiveis")?.focus();
    };
  }, []);
  // O texto só é apagado depois que a API aceitou o comentário; se falhar, continua no campo.
  async function submitComment(formEvent) {
    formEvent.preventDefault();
    if (commenting) return;
    if (await addComment(event.id, comment)) setComment("");
  }
  return (
    <div className="modal-backdrop" role="presentation">
      <section ref={dialogRef} className={`event-dialog cat-${slug}`} role="dialog" aria-modal="true" aria-label={event.title}>
        <header className="dialog-header">
          <CategoryIcon slug={slug} size={96} className="dialog-header-art" />
          <span className="event-type"><CategoryIcon slug={slug} size={15} />{event.category}</span>
          <h2>{event.title}</h2>
          <button ref={closeRef} className="icon-button" type="button" aria-label="Fechar" onClick={close}><X size={18} aria-hidden="true" /></button>
        </header>
        <div className="dialog-body">
          {notice && <Notice alert={notice} onClose={onCloseNotice} />}
          <p>{event.description}</p>
          <div className="dialog-stats">
            <div>
              <CalendarDays size={18} aria-hidden="true" />
              <strong><time dateTime={`${event.date}T${event.time}`}>{formatEventDateLong(event.date)}</time></strong>
              <span>às {event.time}</span>
            </div>
            <div><UsersRound size={18} aria-hidden="true" /><strong>{event.confirmed.length}/{event.capacity}</strong><span>confirmados</span></div>
            <div><UsersRound size={18} aria-hidden="true" /><strong>{event.waitlist.length}</strong><span>na lista de espera</span></div>
          </div>
          <div className="organizer-line">
            <span><MapPin size={15} aria-hidden="true" /> {event.place} - CEP {event.cep || "não informado"}</span>
            <span className="organizer-rating"><Star size={14} aria-hidden="true" />Reputação {event.organizer.honor.toFixed(1)}</span>
          </div>
          <div className="dialog-links">
            <a className="ghost-action" href={mapUrl(event)} target="_blank" rel="noopener noreferrer">
              <ExternalLink size={16} aria-hidden="true" />
              Ver no mapa
              <span className="sr-only">(abre em nova aba)</span>
            </a>
            <button className="ghost-action" type="button" onClick={() => downloadIcs(event)}>
              <CalendarPlus size={16} aria-hidden="true" />
              Adicionar à agenda
            </button>
          </div>
          <div className="card-actions">
            <button className="primary-action" type="button" disabled={event.status !== "ABERTO"} aria-disabled={enrolling || undefined} onClick={() => toggleEnrollment(event.id)}>
              {enrolling ? "Enviando..." : getEnrollmentActionLabel(event, enrollment)}
            </button>
            <button className="secondary-action" type="button" onClick={() => reportEvent(event.id)}>
              <Flag size={16} aria-hidden="true" />
              Denunciar
            </button>
            {canManage && (
              <button className="danger-action" type="button" aria-disabled={deleting || undefined} onClick={() => deleteEvent(event.id)}>
                <Trash2 size={16} aria-hidden="true" />
                {deleting ? "Excluindo..." : "Excluir"}
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
                <button className="secondary-action" type="submit" aria-disabled={commenting || undefined}>
                  {commenting ? "Enviando..." : "Enviar comentário"}
                </button>
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
