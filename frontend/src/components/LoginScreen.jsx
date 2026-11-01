import { AlertTriangle, LogIn, Mail, UserPlus } from "lucide-react";
import { useState } from "react";
import Notice from "./Notice";
import Brand from "./Brand";
import Landscape from "./Landscape";
import ThemeToggle from "./ThemeToggle";
import PasswordField from "./PasswordField";
import { cleanErrorMessage } from "../lib/api";
import { latestBirthDate } from "../lib/eventos";
import { useDemora, AVISO_SERVIDOR_ACORDANDO } from "../lib/useDemora";
const DEMO_PASSWORD = "admin123";
const DEMO_ACCOUNTS = [
  { email: "pedro@chegaai.dev", label: "Pedro", detail: "Embaixador · Reputação 4.9" },
  { email: "caio@chegaai.dev", label: "Caio", detail: "Embaixador · Reputação 4.9" },
  { email: "marcos@chegaai.dev", label: "Marcos", detail: "Embaixador · Reputação 5.0" },
  { email: "baixa@chegaai.dev", label: "Baixa", detail: "Reputação baixa · 1.5" },
  { email: "moderador@chegaai.dev", label: "Moderador", detail: "Modera os rolés da demonstração" },
  { email: "tester.pendente@chegaai.dev", label: "Tester", detail: "Avaliações pendentes" },
];
export default function LoginScreen({ onLogin, onRegister, notice, onCloseNotice }) {
  const [mode, setMode] = useState("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const submittingSlow = useDemora(submitting);

  async function submit(action) {
    setError("");
    setSubmitting(true);
    try {
      await action();
    } catch (err) {
      setError(cleanErrorMessage(err.message));
    } finally {
      setSubmitting(false);
    }
  }

  function handleLogin(e) {
    e.preventDefault();
    submit(() => onLogin(email.trim().toLowerCase(), password));
  }

  function handleRegister(e) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    // O limite do BCrypt é em bytes: uma letra com acento ocupa dois. A API confere de novo.
    if (new TextEncoder().encode(form.get("password")).length > 72) {
      setError("A senha pode ter no máximo 72 bytes; letras com acento contam como dois.");
      return;
    }
    submit(() => onRegister({
      name: form.get("name").trim(),
      email: form.get("email").trim().toLowerCase(),
      cpf: form.get("cpf").replace(/\D/g, "") || null,
      birthDate: form.get("birthDate"),
      password: form.get("password"),
    }));
  }

  function switchMode(nextMode) {
    setMode(nextMode);
    setError("");
  }

  function handleQuickSelect(demoEmail) {
    setEmail(demoEmail);
    setPassword(DEMO_PASSWORD);
    setError("");
  }

  return (
    <main className="login-shell">
      <div className="login-theme"><ThemeToggle /></div>
      <section className="login-intro" aria-label="Sobre o ChegaAí">
        <div className="login-intro-copy">
          <p className="eyebrow">Rolés ao ar livre no Rio</p>
          <h1>O próximo rolé começa lá fora.</h1>
          <p>Futebol no Aterro, patinação na orla ou passeio com os pets: encontre companhia, confira a reputação de quem organiza e participe.</p>
        </div>
        <Landscape className="login-art" />
      </section>
      <section className="login-card">
        <Brand />
        {notice && <Notice alert={notice} onClose={onCloseNotice} />}

        {mode === "login" ? (
          <form key="login" onSubmit={handleLogin} className="login-form">
            {error && <LoginError message={error} />}

            <label>
              E-mail
              <span className="input-with-icon">
                <Mail size={16} aria-hidden="true" />
                <input
                  data-testid="input-email"
                  type="email"
                  autoComplete="username"
                  value={email}
                  onChange={(e) => { setEmail(e.target.value); setError(""); }}
                  placeholder="seu@email.com"
                  required
                />
              </span>
            </label>

            <PasswordField
              label="Senha"
              data-testid="input-password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => { setPassword(e.target.value); setError(""); }}
              placeholder="••••••••"
              required
            />

            <div className="login-links">
              <button data-testid="link-cadastro" type="button" className="login-link" onClick={() => switchMode("register")}>
                Não tem uma conta? <strong>Cadastre-se</strong>
              </button>
            </div>

            <button data-testid="btn-login" className="primary-action" type="submit" disabled={submitting}>
              <LogIn size={18} aria-hidden="true" />
              {submitting ? "Entrando..." : "Entrar"}
            </button>
          </form>
        ) : (
          <form key="register" onSubmit={handleRegister} className="login-form" aria-describedby="aviso-cadastro">
            {error && <LoginError message={error} />}
            <p id="aviso-cadastro" className="login-hint">Versão de demonstração: não use seus dados reais. O CPF é opcional.</p>

            <label>
              Nome completo
              <input data-testid="input-nome" name="name" autoComplete="name" required minLength="2" maxLength="120" placeholder="Seu nome" />
            </label>
            <label>
              E-mail
              <input data-testid="input-email-cadastro" name="email" type="email" autoComplete="email" required maxLength="254" placeholder="seu@email.com" />
            </label>
            <label>
              CPF (opcional)
              <input data-testid="input-cpf" name="cpf" inputMode="numeric" pattern="[0-9.\-]{11,14}" maxLength="14" placeholder="000.000.000-00" />
            </label>
            <div className="field">
              <label htmlFor="cadastro-nascimento">Data de nascimento</label>
              <input id="cadastro-nascimento" data-testid="input-nascimento" name="birthDate" type="date" autoComplete="bday" required
                max={latestBirthDate()} aria-describedby="cadastro-nascimento-dica" />
              <small id="cadastro-nascimento-dica" className="field-hint">É preciso ter 18 anos ou mais.</small>
            </div>
            <PasswordField
              label="Senha"
              hint="Entre 8 e 72 caracteres."
              data-testid="input-senha-cadastro"
              name="password"
              autoComplete="new-password"
              required
              minLength="8"
              maxLength="72"
            />

            <div className="login-links">
              <button type="button" className="login-link" onClick={() => switchMode("login")}>
                Já tem uma conta? <strong>Entrar</strong>
              </button>
            </div>

            <button data-testid="btn-cadastrar" className="primary-action" type="submit" disabled={submitting}>
              <UserPlus size={18} aria-hidden="true" />
              {submitting ? "Criando conta..." : "Criar conta"}
            </button>
          </form>
        )}

        {submittingSlow && <p className="login-hint" role="status">{AVISO_SERVIDOR_ACORDANDO}</p>}

        {mode === "login" && (
          <>
            <div className="login-divider">
              <span>Contas de demonstração</span>
            </div>

            <div className="login-accounts">
              {DEMO_ACCOUNTS.map((account) => (
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
              <p className="login-hint">Clique em uma conta para preencher automaticamente. Senha: <code>{DEMO_PASSWORD}</code></p>
            </div>
          </>
        )}
      </section>
    </main>
  );
}
function LoginError({ message }) {
  return (
    <div className="login-error" role="alert">
      <AlertTriangle size={16} aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}
