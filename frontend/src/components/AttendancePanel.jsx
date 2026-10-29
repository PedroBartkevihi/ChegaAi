import { useEffect, useRef, useState } from "react";

const formatHonor = (value) => new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 }).format(Number(value));

export default function AttendancePanel({ eventId, management, pending, reload, changePresence, concludeEvent }) {
  const [review, setReview] = useState(false);
  const cancelRef = useRef(null);
  const concludeRef = useRef(null);
  const data = management?.data;
  const savingPresence = Object.keys(pending).some((key) => key.startsWith(`presenca:${eventId}:`));
  const concluding = Boolean(pending[`conclusao:${eventId}`]);
  useEffect(() => { if (review) cancelRef.current?.focus(); }, [review]);
  useEffect(() => { if (data?.status !== "ATIVO") setReview(false); }, [data?.status]);

  const refreshing = management?.state === "loading";
  if (!management || (refreshing && !data)) return <section className="participants-panel" aria-label="Presenças"><p role="status">Carregando presenças...</p></section>;
  if (management.state === "error") return <section className="participants-panel" aria-label="Presenças"><p>Não foi possível carregar as presenças.</p><button className="secondary-action" type="button" onClick={reload}>Tentar novamente</button></section>;
  const missing = data.participantes.filter((person) => person.status === "CONFIRMADA" && !person.organizador && !person.compareceu);
  const deduction = Number(data.descontoPorAusencia);
  async function finish() {
    if (concluding || savingPresence) return;
    if (await concludeEvent(eventId)) setReview(false);
  }
  function returnToList() {
    setReview(false);
    requestAnimationFrame(() => concludeRef.current?.focus());
  }
  return (
    <section className="participants-panel" aria-label="Presenças do rolé" aria-busy={refreshing || undefined}>
      <div className="section-heading compact-heading"><h3>Presenças do rolé</h3><button className="ghost-action" type="button" disabled={savingPresence || concluding || refreshing} onClick={reload}>Atualizar</button></div>
      {refreshing && <p role="status">Atualizando presenças...</p>}
      {review && data.podeConcluir ? (
        <section className="conclusion-review" aria-label="Confirmar conclusão">
          <h4>Concluir este rolé?</h4>
          <p>Confira as presenças antes de concluir. Depois, elas não poderão ser alteradas.</p>
          {missing.length ? <>
            <p><strong>{missing.length} {missing.length === 1 ? "participante será marcado ausente" : "participantes serão marcados ausentes"}.</strong></p>
            <ul>{missing.map((person) => <li key={person.id}>{person.nome}</li>)}</ul>
            <p>Cada ausência desconta <strong>{formatHonor(deduction)}</strong> de honra. Total: {missing.length} × {formatHonor(deduction)} = <strong>{formatHonor(missing.length * deduction)}</strong>.</p>
          </> : <p>Todos os participantes têm presença registrada. Ninguém receberá desconto por ausência.</p>}
          <p>A presença do organizador é automática quando ele está inscrito. As avaliações dos participantes ficam disponíveis após a conclusão.</p>
          <div className="card-actions"><button ref={cancelRef} className="secondary-action" type="button" disabled={concluding} onClick={returnToList}>Conferir presenças</button><button className="primary-action" type="button" disabled={concluding || savingPresence} onClick={finish}>{concluding ? "Concluindo..." : "Confirmar conclusão"}</button></div>
        </section>
      ) : <>
        {data.participantes.length ? <ul className="participant-list">{data.participantes.map((person) => {
          const saving = Boolean(pending[`presenca:${eventId}:${person.id}`]);
          return <li key={person.id} className="participant-row">
            <div><strong>{person.nome}</strong><span className="muted">{person.organizador ? "Organizador · presença automática" : person.status === "AUSENTE" ? "Ausente" : person.compareceu ? "Presença registrada" : "Sem presença registrada"}</span></div>
            {!person.organizador && <label className="presence-toggle"><input type="checkbox" checked={Boolean(person.compareceu)} disabled={!data.podeMarcarPresenca || savingPresence || concluding || refreshing || person.status !== "CONFIRMADA"} onChange={(event) => changePresence(eventId, person.id, event.target.checked)} /><span>{saving ? "Salvando..." : "Presente"}<span className="sr-only">: {person.nome}</span></span></label>}
          </li>;
        })}</ul> : <p className="muted">Nenhum participante inscrito neste rolé.</p>}
        {!data.podeMarcarPresenca && data.status === "ATIVO" && <p className="field-hint">As presenças podem ser registradas entre o início do rolé e a conclusão. Use Atualizar quando o rolé começar.</p>}
        {data.podeConcluir && <button ref={concludeRef} className="primary-action" type="button" disabled={savingPresence || concluding || refreshing} onClick={() => setReview(true)}>Concluir rolé</button>}
      </>}
    </section>
  );
}
