import { Eye, EyeOff } from "lucide-react";
import { useId, useState } from "react";
// Campo de senha com botão de mostrar/ocultar. O botão fica fora do <label>, para não entrar no nome do campo.
export default function PasswordField({ label, hint, ...inputProps }) {
  const [visible, setVisible] = useState(false);
  const id = useId();
  const hintId = useId();
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <span className="input-with-action">
        <input id={id} type={visible ? "text" : "password"} aria-describedby={hint ? hintId : undefined} {...inputProps} />
        <button type="button" className="input-action" aria-pressed={visible} onClick={() => setVisible((current) => !current)}>
          {visible ? <EyeOff size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />}
          Mostrar<span className="sr-only"> senha</span>
        </button>
      </span>
      {hint && <small id={hintId} className="field-hint">{hint}</small>}
    </div>
  );
}
