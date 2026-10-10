import { AlertTriangle, LogIn, Search } from "lucide-react";
import { useState } from "react";
export default function LoginScreen({ onLogin, loading }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const MOCK_ACCOUNTS = [
    { email: "pedro@chegaai.dev", label: "Pedro", detail: "Embaixador · Reputação 4.9" },
    { email: "caio@chegaai.dev", label: "Caio", detail: "Embaixador · Reputação 4.9" },
    { email: "marcos@chegaai.dev", label: "Marcos", detail: "Embaixador · Reputação 5.0" },
    { email: "baixa@chegaai.dev", label: "Baixa", detail: "Reputação baixa · 1.5" },
    { email: "moderador@chegaai.dev", label: "Moderador", detail: "Administrador" },
    { email: "tester.pendente@chegaai.dev", label: "Tester", detail: "Avaliações pendentes" },
  ];

  const VALID_EMAILS = MOCK_ACCOUNTS.map((a) => a.email.toLowerCase());

  function handleSubmit(e) {
    e.preventDefault();
    setError("");
    const trimmedEmail = email.trim().toLowerCase();
    if (!VALID_EMAILS.includes(trimmedEmail)) {
      setError("E-mail ou senha inválidos.");
      return;
    }
    if (password !== "admin123") {
      setError("E-mail ou senha inválidos.");
      return;
    }
    onLogin(trimmedEmail);
  }

  function handleQuickSelect(mockEmail) {
    setEmail(mockEmail);
    setPassword("admin123");
    setError("");
  }

  return (
    <main className="app-shell" style={{ display: "flex", justifyContent: "center", alignItems: "center" }}>
      <section className="login-card">
        <div className="brand" style={{ marginBottom: "1.5rem" }}>
          <span className="brand-mark" aria-hidden="true">Aí</span>
          <div>
            <strong>ChegaAí</strong>
            <small>Companhia para sair de casa</small>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="login-form">
          {error && (
            <div className="login-error" role="alert">
              <AlertTriangle size={16} />
              <span>{error}</span>
            </div>
          )}

          <label>
            E-mail
            <span className="input-with-icon">
              <Search size={16} />
              <input
                data-testid="input-email"
                type="email"
                value={email}
                onChange={(e) => { setEmail(e.target.value); setError(""); }}
                placeholder="seu@email.com"
                required
              />
            </span>
          </label>

          <label>
            Senha
            <input
              data-testid="input-password"
              type="password"
              value={password}
              onChange={(e) => { setPassword(e.target.value); setError(""); }}
              placeholder="••••••••"
              required
            />
          </label>

          <div className="login-links">
            <button
              type="button"
              className="login-link"
              onClick={() => window.alert("Funcionalidade em desenvolvimento. Disponível na próxima versão.")}
            >
              Não tem uma conta? <strong>Cadastre-se</strong>
            </button>
            <button
              type="button"
              className="login-link"
              onClick={() => window.alert("Um link de recuperação fictício foi enviado para o seu e-mail.")}
            >
              Esqueci minha senha
            </button>
          </div>

          <button data-testid="btn-login" className="primary-action" type="submit" disabled={loading}>
            <LogIn size={18} />
            {loading ? "Entrando..." : "Entrar"}
          </button>
        </form>

        <div className="login-divider">
          <span>Contas de demonstração</span>
        </div>

        <div className="login-accounts">
          {MOCK_ACCOUNTS.map((account) => (
            <button
              key={account.email}
              type="button"
              className={`login-account-card${email === account.email ? " selected" : ""}`}
              onClick={() => handleQuickSelect(account.email)}
            >
              <span className="avatar" aria-hidden="true">{account.label[0]}</span>
              <div>
                <strong>{account.label}</strong>
                <small>{account.email}</small>
              </div>
              <span className="login-account-badge">{account.detail}</span>
            </button>
          ))}
          <p className="login-hint">Clique em uma conta para preencher automaticamente. Senha: <code>admin123</code></p>
        </div>
      </section>
    </main>
  );
}
