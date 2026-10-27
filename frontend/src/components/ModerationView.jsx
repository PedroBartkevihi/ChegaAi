import StackItem from "./StackItem";
import { categories } from "../lib/eventos";
export default function ModerationView({ reports, resolveReport }) {
  const pendingReports = reports.filter((report) => report.status === "PENDENTE");
  return (
    <section className="view" aria-label="Painel de moderação">
      <div className="page-heading">
        <p className="eyebrow">Moderação</p>
        <h1>Denúncias e travas</h1>
      </div>
      <div className="moderation-grid">
        <section>
          <h2>Denúncias pendentes</h2>
          <div className="stack-list">
            {pendingReports.length ? (
              pendingReports.map((report) => (
                <article className="stack-item" key={report.id}>
                  <strong>{report.target}</strong>
                  <p>{report.reason}</p>
                  <span className="muted">Denunciante: {report.author}</span>
                  <div className="card-actions">
                    <button className="secondary-action" type="button" onClick={() => resolveReport(report.id, false)}>Arquivar</button>
                    <button className="danger-action" type="button" onClick={() => resolveReport(report.id, true)}>Travar</button>
                  </div>
                </article>
              ))
            ) : (
              <div className="empty-state">Sem denúncias pendentes.</div>
            )}
          </div>
        </section>
        <section>
          <h2>Categorias ativas</h2>
          <div className="stack-list">
            {categories.map((category) => <StackItem key={category} title={category} body="Categoria ativa no feed." />)}
          </div>
        </section>
      </div>
    </section>
  );
}
