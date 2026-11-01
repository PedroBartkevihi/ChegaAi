import {
  CalendarDays,
  CalendarPlus,
  ExternalLink,
  Flag,
  MapPin,
  RefreshCw,
  Star,
  Trash2,
  UsersRound,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import CategoryIcon from "./CategoryIcon";
import Notice from "./Notice";
import AttendancePanel from "./AttendancePanel";
import { getEnrollmentActionLabel, formatEventDateLong, categorySlug, eventTimestamp } from "../lib/eventos";
import { downloadIcs, mapUrl } from "../lib/agenda";
import { returnFocus } from "../lib/foco";
const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
// Abre com o resumo do card; CEP e comentários chegam com o detalhe (GET /eventos/{id}), em detailState.
export default function EventModal({ event, detailState = "ready", reloadDetail, enrollment, enrollmentsReady = true, pending = {}, close, notice, onCloseNotice, toggleEnrollment, addComment, deleteEvent, reportEvent, currentUser, inactive = false, management, reloadManagement, changePresence, concludeEvent, hasPendingRatings, openRatings }) {
  const [comment, setComment] = useState("");
  const dialogRef = useRef(null);
  const closeRef = useRef(null);
  // O App recria a função close a cada renderização; guardá-la num ref evita refazer o efeito
  // abaixo (e devolver o foco ao botão de fechar) toda vez que a lista de eventos atualiza.
  const onCloseRef = useRef(close);
  onCloseRef.current = close;
  const detailReady = detailState === "ready";
  // Como a API: depois de concluído o rolé aceita comentários; travado ou cancelado, não.
  const closedReason = event.status === "TRAVADO" ? "Este rolé está travado pela moderação e não aceita comentários."
    : event.status === "CANCELADO" ? "Este rolé foi cancelado e não aceita comentários." : null;
  const canComment = detailReady && enrollmentsReady && !closedReason && (enrollment === "CONFIRMADO" || enrollment === "FILA");
  const isOrganizer = event.organizer.id === currentUser?.id;
  const activeRef = useRef(!inactive);
  activeRef.current = !inactive;
  const canManage = currentUser?.isModerator || event.organizer.id === currentUser?.id;
  const slug = categorySlug(event.category);
  const enrolling = Boolean(pending[`inscricao:${event.id}`]);
  const deleting = Boolean(pending[`exclusao:${event.id}`]);
  const commenting = Boolean(pending[`comentario:${event.id}`]);
  // Ao abrir, o foco vai para o botão de fechar; Esc fecha; Tab circula só dentro do diálogo;
  // ao fechar, o foco volta para quem abriu o diálogo (ver returnFocus).
  useEffect(() => {
    const opener = document.activeElement;
    closeRef.current?.focus();
    function handleKeyDown(keyEvent) {
      if (!activeRef.current) return;
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
      returnFocus(opener);
    };
  }, []);
  // O texto só é apagado depois que a API aceitou o comentário (se falhar, continua no campo), e só
  // se ainda for o que foi enviado; durante o envio o campo fica só para leitura.
  async function submitComment(formEvent) {
    formEvent.preventDefault();
    if (commenting) return;
    const sent = comment;
    if (await addComment(event.id, sent)) setComment((current) => (current === sent ? "" : current));
  }
  return (
    <div className="modal-backdrop" role="presentation">
      <section ref={dialogRef} className={`event-dialog cat-${slug}`} role="dialog" aria-modal={!inactive} aria-hidden={inactive || undefined} inert={inactive || undefined} aria-label={event.title}>
        <header className="dialog-header">
          <CategoryIcon slug={slug} size={96} className="dialog-header-art" />
          <span className="event-type"><CategoryIcon slug={slug} size={15} />{event.category}</span>
          <h2>{event.title}</h2>
          <button ref={closeRef} className="icon-button" type="button" aria-label="Fechar" onClick={close}><X size={18} aria-hidden="true" /></button>
        </header>
        <div className="dialog-body">
          {/* Fixo no topo da área que rola, para o aviso aparecer mesmo com o detalhe rolado. */}
          {notice && <div className="dialog-notice"><Notice alert={notice} onClose={onCloseNotice} /></div>}
          <p>{event.description}</p>
          <div className="dialog-stats">
            <div>
              <CalendarDays size={18} aria-hidden="true" />
              <strong><time dateTime={`${event.date}T${event.time}`}>{formatEventDateLong(event.date)}</time></strong>
              <span>às {event.time}</span>
            </div>
            <div><UsersRound size={18} aria-hidden="true" /><strong>{event.confirmedCount}/{event.capacity}</strong><span>confirmados</span></div>
            <div><UsersRound size={18} aria-hidden="true" /><strong>{event.waitlistCount}</strong><span>na lista de espera</span></div>
          </div>
          <div className="organizer-line">
            <span><MapPin size={15} aria-hidden="true" /> {event.place}{detailReady && ` - CEP ${event.cep || "não informado"}`}</span>
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
            <button className="primary-action" type="button" disabled={!enrollmentsReady || isOrganizer || event.status !== "ABERTO" || eventTimestamp(event) <= Date.now()} aria-disabled={enrolling || undefined} onClick={() => toggleEnrollment(event.id)}>
              {enrolling ? "Enviando..." : !enrollmentsReady ? "Carregando inscrição..." : isOrganizer ? "Você organiza este rolé" : getEnrollmentActionLabel(event, enrollment)}
            </button>
            <button className="secondary-action" type="button" onClick={() => reportEvent(event.id)}>
              <Flag size={16} aria-hidden="true" />
              Denunciar
            </button>
            {canManage && event.status !== "CONCLUIDO" && (
              <button className="danger-action" type="button" aria-disabled={deleting || undefined} onClick={() => deleteEvent(event.id)}>
                <Trash2 size={16} aria-hidden="true" />
                {deleting ? "Excluindo..." : "Excluir"}
              </button>
            )}
          </div>
          {isOrganizer && <AttendancePanel eventId={event.id} management={management} pending={pending} reload={reloadManagement} changePresence={changePresence} concludeEvent={concludeEvent} />}
          {event.status === "CONCLUIDO" && hasPendingRatings && <button className="primary-action" type="button" onClick={() => openRatings(event.id)}>Avaliar participantes</button>}
          <section className="comments-box">
            <h3>Comentários do rolé</h3>
            {detailState === "loading" ? (
              <div className="empty-state" role="status">Carregando comentários...</div>
            ) : detailState === "error" ? (
              <div className="empty-state">
                <p>Não foi possível carregar os comentários.</p>
                <button className="secondary-action" type="button" onClick={reloadDetail}>
                  <RefreshCw size={16} aria-hidden="true" />
                  Tentar de novo
                </button>
              </div>
            ) : event.comments.length ? (
              event.comments.map((item, index) => (
                <article className="comment-item" key={`${item.author}-${item.time}-${index}`}>
                  <header><strong>{item.author}</strong><span className="muted">{item.time}</span></header>
                  <p>{item.text}</p>
                </article>
              ))
            ) : (
              <div className="empty-state">Sem comentários ainda.</div>
            )}
            {!detailReady ? null : canComment ? (
              <form className="comment-form" onSubmit={submitComment}>
                <label>
                  Novo comentário
                  <textarea value={comment} readOnly={commenting} onChange={(event) => setComment(event.target.value)} required maxLength="180" rows="3" placeholder="Escreva seu comentário para o grupo..." />
                </label>
                <button className="secondary-action" type="submit" aria-disabled={commenting || undefined}>
                  {commenting ? "Enviando..." : "Enviar comentário"}
                </button>
              </form>
            ) : (
              <div className="empty-state">{closedReason || "Apenas participantes confirmados ou na lista de espera podem comentar."}</div>
            )}
          </section>
        </div>
      </section>
    </div>
  );
}
