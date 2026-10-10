import { Plus } from "lucide-react";
export default function CreateView({ categoryOptions, capacityLimit, onSubmit }) {
  return (
    <section className="view" aria-label="Criar evento">
      <form className="event-form" onSubmit={onSubmit}>
        <div className="form-heading">
          <span className="status-pill">Criar Evento</span>
          <h2>Novo rolé</h2>
          <p>Defina os detalhes do seu novo evento com limite de até {capacityLimit} participantes.</p>
        </div>
        <div className="form-grid">
          <label>Título<input name="title" required maxLength="80" placeholder="Patinação na orla" /></label>
          <label>Categoria<select name="category" required>{categoryOptions.map((category) => <option key={category} value={category}>{category}</option>)}</select></label>
          <label>Data<input name="date" required type="date" /></label>
          <label>Horário<input name="time" required type="time" /></label>
          <label>CEP<input name="cep" inputMode="numeric" maxLength="9" placeholder="22290-240" /></label>
          <label>Cidade / bairro<input name="place" required maxLength="90" placeholder="Botafogo, Rio de Janeiro" /></label>
          <label>Limite de participantes<input data-testid="input-capacidade" name="capacity" required min="3" max={capacityLimit} type="number" defaultValue="20" /></label>
          <label>Distância estimada<input name="distance" required min="1" max="80" type="number" defaultValue="8" /></label>
        </div>
        <label>
          Descrição
          <textarea name="description" required maxLength="260" rows="4" placeholder="Descreva o ponto de encontro, ritmo do rolé e combinados de segurança." />
        </label>
        <div className="form-actions">
          <button className="secondary-action" type="reset">Limpar</button>
          <button className="primary-action" type="submit"><Plus size={18} />Publicar rolé</button>
        </div>
      </form>
    </section>
  );
}
