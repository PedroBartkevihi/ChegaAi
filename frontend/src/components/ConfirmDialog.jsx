import { AlertTriangle } from "lucide-react";
import { useEffect, useId, useRef } from "react";
import { returnFocus } from "../lib/foco";
const FOCUSABLE = "button:not([disabled])";
// Diálogo de confirmação no estilo do site, no lugar do window.confirm. Pode abrir por cima do detalhe
// do rolé: ouve o teclado na fase de captura e para a propagação, para só ele tratar Esc e Tab.
export default function ConfirmDialog({ title, message, confirmLabel, cancelLabel = "Voltar", tone = "primary", onConfirm, onCancel }) {
  const dialogRef = useRef(null);
  const cancelRef = useRef(null);
  const onCancelRef = useRef(onCancel);
  onCancelRef.current = onCancel;
  const titleId = useId();
  const messageId = useId();
  useEffect(() => {
    const opener = document.activeElement;
    cancelRef.current?.focus();
    function handleKeyDown(keyEvent) {
      if (keyEvent.key === "Escape") {
        keyEvent.preventDefault();
        keyEvent.stopPropagation();
        onCancelRef.current();
        return;
      }
      if (keyEvent.key !== "Tab" || !dialogRef.current) return;
      keyEvent.stopPropagation();
      const focusable = [...dialogRef.current.querySelectorAll(FOCUSABLE)];
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
    document.addEventListener("keydown", handleKeyDown, true);
    return () => {
      document.removeEventListener("keydown", handleKeyDown, true);
      returnFocus(opener);
    };
  }, []);
  return (
    <div className="modal-backdrop confirm-backdrop" role="presentation" onClick={(event) => event.target === event.currentTarget && onCancel()}>
      <section ref={dialogRef} className="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={messageId}>
        <span className={`confirm-icon ${tone}`} aria-hidden="true"><AlertTriangle size={20} /></span>
        <h2 id={titleId}>{title}</h2>
        <p id={messageId}>{message}</p>
        <div className="confirm-actions">
          <button ref={cancelRef} className="secondary-action" type="button" onClick={onCancel}>{cancelLabel}</button>
          <button className={tone === "danger" ? "danger-action" : "primary-action"} type="button" onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </section>
    </div>
  );
}
