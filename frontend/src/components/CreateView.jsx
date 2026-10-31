import { Plus } from "lucide-react";
import { todayInputValue } from "../lib/eventos";
export default function CreateView({ categoryOptions, capacityLimit, onSubmit, submitting = false }) {
  return (
    <section className="view" aria-label="Criar evento">
      <div className="page-heading">
        <p className="eyebrow">Criar rolé</p>
        <h1>Novo rolé</h1>
        <p>Combine a atividade, o horário e o local. Na descrição, conte onde o grupo se encontra e o que levar.</p>
      </div>
      <form className="event-form" onSubmit={onSubmit}>
        <fieldset>
          <legend>A atividade</legend>
          <div className="form-grid">
            <label>Título<input name="title" required minLength="3" maxLength="80" placeholder="Patinação na orla" /></label>
            <label>Categoria<select name="category" required>{categoryOptions.map((category) => <option key={category} value={category}>{category}</option>)}</select></label>
          </div>
          <label>
            Descrição
            <textarea name="description" required minLength="10" maxLength="260" rows="4" placeholder="Ponto de encontro, ritmo do rolé e o que levar (água, protetor solar...)." />
          </label>
        </fieldset>
        <fieldset>
          <legend>Quando e onde</legend>
          <div className="form-grid">
            <label>Data<input name="date" required type="date" min={todayInputValue()} /></label>
            <label>Horário<input name="time" required type="time" /></label>
            <label>CEP<input name="cep" inputMode="numeric" maxLength="9" placeholder="22290-240" /></label>
            <label>Cidade / bairro<input name="place" required maxLength="90" placeholder="Botafogo, Rio de Janeiro" /></label>
          </div>
        </fieldset>
        <fieldset>
          <legend>Quem vai</legend>
          <div className="form-grid">
            <label>
              Limite de participantes
              <input data-testid="input-capacidade" name="capacity" required min="3" max={capacityLimit} type="number" defaultValue={capacityLimit} />
              <small className="field-hint">Até {capacityLimit} participantes na sua reputação atual.</small>
            </label>
          </div>
        </fieldset>
        <div className="form-actions">
          <button className="secondary-action" type="reset">Limpar</button>
          <button className="primary-action" type="submit" aria-disabled={submitting || undefined}>
            <Plus size={18} aria-hidden="true" />
            {submitting ? "Publicando..." : "Publicar rolé"}
          </button>
        </div>
      </form>
    </section>
  );
}
