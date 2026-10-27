import {
  AlertTriangle,
  CheckCircle2,
  Info,
  X,
} from "lucide-react";
export default function Notice({ alert, onClose }) {
  const Icon = alert.type === "error"
    ? AlertTriangle
    : alert.type === "success"
      ? CheckCircle2
      : Info;
  return (
    <div className={`notice alert-${alert.type}`} role={alert.type === "error" ? "alert" : "status"}>
      <Icon size={20} aria-hidden="true" />
      <div>{alert.message}</div>
      <button className="notice-close" type="button" aria-label="Fechar alerta" onClick={onClose}>
        <X size={18} aria-hidden="true" />
      </button>
    </div>
  );
}
