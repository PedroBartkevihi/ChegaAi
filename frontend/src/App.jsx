import {
  AlertTriangle,
  CalendarDays,
  CheckCircle2,
  Flag,
  Info,
  LogIn,
  MapPin,
  Plus,
  Search,
  Star,
  Trash2,
  UsersRound,
  X,
} from "lucide-react";
import React, { useEffect, useMemo, useState, useCallback } from "react";

// ============================================================================
// API Helper
// ============================================================================

const API_BASE = import.meta.env.VITE_API_BASE_URL || "/api";

async function api(path, options = {}) {
  const userId = localStorage.getItem("chegaae.userId");
  const headers = {
    "Content-Type": "application/json",
    ...(userId && { "X-User-Id": userId }),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(error.message || `Erro ${response.status}`);
  }

  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

// ============================================================================
// Error Message Cleaner
// ============================================================================

function cleanErrorMessage(msg) {
  if (!msg) return "Ocorreu um erro inesperado. Tente novamente.";
  let clean = msg;
  
  // Remove technical prefixes and suffixes (e.g. RN001, UC03)
  clean = clean.replace(/RN\d+/gi, "").replace(/UC\d+/gi, "");
  
  if (clean.includes("honra") || clean.includes("Honra") || clean.includes("reputacao") || clean.includes("Reputação")) {
    if (clean.toLowerCase().includes("abaixo") || clean.toLowerCase().includes("insuficiente") || clean.toLowerCase().includes("limite")) {
      return "Ação Não Permitida. Sua reputação atual na plataforma está abaixo do limite mínimo necessário para realizar esta ação.";
    }
  }
  if (clean.includes("vagas") || clean.includes("capacidade") || clean.includes("lotado")) {
    return "Limite de vagas atingido. O evento está lotado no momento.";
  }
  
  clean = clean.replace(/^[\s\-\:\⚠️\❌]+/, "").trim();
  if (clean) {
    clean = clean.charAt(0).toUpperCase() + clean.slice(1);
  }
  return clean;
}

// ============================================================================
// Constants
// ============================================================================

const categories = ["Futebol", "Pets", "Patinação", "Passeio", "Esporte", "Cultura", "Lazer"];

const initialFilters = {
  search: "",
  category: "todos",
  distance: "todos",
  honor: "0",
};

// ============================================================================
// Main App
// ============================================================================

export default function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const [events, setEvents] = useState([]);
  const [enrollments, setEnrollments] = useState({});
  const [reports, setReports] = useState([]);
  const [view, setView] = useState("feed");
  const [filters, setFilters] = useState(initialFilters);
  const [selectedEventId, setSelectedEventId] = useState(null);
  const [alert, setAlert] = useState(null); // { type: 'success' | 'error' | 'warning' | 'info', message: string }
  const [loading, setLoading] = useState(false);

  const capacityLimit = currentUser && isAmbassador(currentUser.honor) ? 50 : 20;
  const selectedEvent = events.find((event) => event.id === selectedEventId);

  // === Carregar eventos do backend ===
  const loadEvents = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api("/eventos");
      setEvents(data);

      // Recalcular enrollments baseado nos eventos carregados
      if (currentUser) {
        const newEnrollments = {};
        data.forEach((event) => {
          if (event.confirmed.includes(currentUser.name)) {
            newEnrollments[event.id] = "CONFIRMADO";
          } else if (event.waitlist.includes(currentUser.name)) {
            newEnrollments[event.id] = "FILA";
          }
        });
        setEnrollments(newEnrollments);
      }
    } catch (err) {
      console.error("Erro ao carregar eventos:", err);
      showNotice("Não foi possível carregar os eventos. Verifique sua conexão ou se o servidor está ativo.", "error");
    } finally {
      setLoading(false);
    }
  }, [currentUser]);

  // Carregar eventos quando o usuário logar
  useEffect(() => {
    if (currentUser) {
      loadEvents();
    }
  }, [currentUser, loadEvents]);

  // Tentar restaurar sessão do localStorage
  useEffect(() => {
    const savedUser = localStorage.getItem("chegaae.user");
    if (savedUser) {
      try {
        setCurrentUser(JSON.parse(savedUser));
      } catch {
        localStorage.removeItem("chegaae.user");
        localStorage.removeItem("chegaae.userId");
      }
    }
  }, []);

  const filteredEvents = useMemo(() => {
    return events
      .filter((event) => {
        const haystack = `${event.title} ${event.category} ${event.place} ${event.description} ${event.organizer.name}`.toLowerCase();
        const matchesCategory = filters.category === "todos" || event.category === filters.category;
        const matchesDistance = filters.distance === "todos" || event.distance <= Number(filters.distance);
        const matchesHonor = event.organizer.honor >= Number(filters.honor);
        return matchesCategory && matchesDistance && matchesHonor && haystack.includes(filters.search.toLowerCase());
      })
      .sort((a, b) => Number(isAmbassador(b.organizer.honor)) - Number(isAmbassador(a.organizer.honor)));
  }, [events, filters]);

  const metrics = useMemo(() => {
    return {
      active: events.filter((event) => event.status === "ABERTO").length,
      waitlist: events.reduce((total, event) => total + event.waitlist.length, 0),
      ambassadors: events.filter((event) => isAmbassador(event.organizer.honor)).length,
    };
  }, [events]);

  function updateFilter(name, value) {
    setFilters((current) => ({ ...current, [name]: value }));
  }

  // === Login via API ===
  async function handleLogin(email) {
    try {
      setLoading(true);
      const user = await api("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email }),
      });
      setCurrentUser(user);
      localStorage.setItem("chegaae.user", JSON.stringify(user));
      localStorage.setItem("chegaae.userId", user.id);
      showNotice(`Bem-vindo(a), ${user.name}!`, "success");
    } catch (err) {
      showNotice("Erro no login: " + err.message, "error");
    } finally {
      setLoading(false);
    }
  }

  function handleLogout() {
    setCurrentUser(null);
    setEvents([]);
    setEnrollments({});
    localStorage.removeItem("chegaae.user");
    localStorage.removeItem("chegaae.userId");
    setView("feed");
  }

  // === Criar evento via API ===
  async function createEvent(formEvent) {
    formEvent.preventDefault();
    const formElement = formEvent.currentTarget;

    if (currentUser.honor < 2) {
      showNotice("Ação Não Permitida. Sua reputação atual está abaixo do limite mínimo necessário para criar novos rolés.", "error");
      return;
    }

    const form = new FormData(formElement);
    const dto = {
      title: form.get("title").trim(),
      category: form.get("category"),
      date: form.get("date"),
      time: form.get("time"),
      cep: form.get("cep")?.trim() || "",
      place: form.get("place").trim(),
      capacity: Math.min(Number(form.get("capacity")), capacityLimit),
      distance: Number(form.get("distance")),
      description: form.get("description").trim(),
    };

    try {
      setLoading(true);
      const criado = await api("/eventos", {
        method: "POST",
        body: JSON.stringify(dto),
      });
      // Recarregar todos os eventos para ter dados consistentes
      await loadEvents();
      formElement.reset();
      setView("feed");
      setSelectedEventId(criado.id);
      showNotice("Rolé criado com sucesso!", "success");
    } catch (err) {
      showNotice("Não foi possível criar o evento: " + err.message, "error");
    } finally {
      setLoading(false);
    }
  }

  // === Toggle inscrição via API ===
  async function toggleEnrollment(eventId) {
    try {
      setLoading(true);
      const result = await api(`/eventos/${eventId}/inscricao`, {
        method: "POST",
      });

      showNotice(result.message, "success");

      // Recarregar eventos para atualizar listas de confirmados/fila
      await loadEvents();
    } catch (err) {
      showNotice("Não foi possível realizar a inscrição: " + err.message, "error");
    } finally {
      setLoading(false);
    }
  }

  // === Adicionar comentário via API ===
  async function addComment(eventId, text) {
    if (!text.trim()) return;
    try {
      const eventoAtualizado = await api(`/eventos/${eventId}/comentarios`, {
        method: "POST",
        body: JSON.stringify({ text: text.trim() }),
      });

      // Atualizar evento localmente com dados do servidor
      setEvents((current) =>
        current.map((event) => (event.id === eventId ? eventoAtualizado : event))
      );
    } catch (err) {
      showNotice("Não foi possível publicar o comentário: " + err.message, "error");
    }
  }

  async function deleteEvent(eventId) {
    const event = events.find((item) => item.id === eventId);
    if (!event) return;

    const canDelete = currentUser.isModerator || event.organizer.id === currentUser.id;
    if (!canDelete) {
      showNotice("Apenas o organizador ou um moderador pode excluir este rolé.", "error");
      return;
    }

    const confirmed = window.confirm(`Excluir o rolé "${event.title}"? Essa ação não pode ser desfeita.`);
    if (!confirmed) return;

    try {
      setLoading(true);
      await api(`/eventos/${eventId}`, { method: "DELETE" });
      setEvents((current) => current.filter((item) => item.id !== eventId));
      setEnrollments((current) => {
        const next = { ...current };
        delete next[eventId];
        return next;
      });
      setSelectedEventId(null);
      showNotice("Rolé excluído com sucesso.", "success");
    } catch (err) {
      showNotice("Não foi possível excluir o evento: " + err.message, "error");
    } finally {
      setLoading(false);
    }
  }

  // === Denúncia (local por enquanto) ===
  function reportEvent(eventId) {
    const event = events.find((item) => item.id === eventId);
    if (!event) return;

    setReports((current) => [
      {
        id: createId(),
        target: event.title,
        author: currentUser.name,
        reason: "Denúncia registrada pelo protótipo para análise manual.",
        status: "PENDENTE",
      },
      ...current,
    ]);
    showNotice("Denúncia enviada à moderação para análise.", "success");
  }

  function resolveReport(reportId, shouldLockEvent) {
    const report = reports.find((item) => item.id === reportId);
    if (!report) return;

    setReports((current) =>
      current.map((item) => (item.id === reportId ? { ...item, status: shouldLockEvent ? "PROCEDENTE" : "IMPROCEDENTE" } : item))
    );
  }

  function showNotice(message, type = "info") {
    const cleanMsg = type === "error" ? cleanErrorMessage(message) : message;
    setAlert({ message: cleanMsg, type });
  }

  useEffect(() => {
    if (alert) {
      const timer = setTimeout(() => setAlert(null), 6000);
      return () => clearTimeout(timer);
    }
  }, [alert]);

  useEffect(() => {
    if (currentUser && !currentUser.isModerator && view === "moderation") {
      setView("feed");
    }
  }, [currentUser, view]);

  // === Tela de login ===
  if (!currentUser) {
    return <LoginScreen onLogin={handleLogin} loading={loading} />;
  }

  return (
    <main className="app-shell">
      <Sidebar
        view={view}
        filters={filters}
        setView={setView}
        updateFilter={updateFilter}
        currentUser={currentUser}
        onLogout={handleLogout}
      />

      <section className="content">
        <header className="topbar">
          <div>
            <p className="eyebrow">Conexões reais e seguras</p>
            <h1>Encontre companhia confiável para rolés presenciais.</h1>
          </div>
          <button className="primary-action" type="button" onClick={() => setView("create")}>
            <Plus size={18} />
            Criar rolé
          </button>
        </header>

        {alert && <Notice alert={alert} onClose={() => setAlert(null)} />}
        {loading && <div className="notice loading-notice">Carregando...</div>}

        {view === "feed" && (
          <FeedView
            events={filteredEvents}
            metrics={metrics}
            filters={filters}
            enrollments={enrollments}
            currentUser={currentUser}
            resetFilters={() => setFilters(initialFilters)}
            openEvent={setSelectedEventId}
            toggleEnrollment={toggleEnrollment}
            deleteEvent={deleteEvent}
          />
        )}

        {view === "create" && <CreateView categoryOptions={categories} capacityLimit={capacityLimit} onSubmit={createEvent} />}
        {view === "profile" && <ProfileView events={events} enrollments={enrollments} currentUser={currentUser} />}
        {view === "moderation" && currentUser.isModerator && <ModerationView reports={reports} resolveReport={resolveReport} />}
      </section>

      {selectedEvent && (
        <EventModal
          event={selectedEvent}
          enrollment={enrollments[selectedEvent.id]}
          close={() => setSelectedEventId(null)}
          toggleEnrollment={toggleEnrollment}
          addComment={addComment}
          deleteEvent={deleteEvent}
          reportEvent={reportEvent}
          currentUser={currentUser}
        />
      )}
    </main>
  );
}

// ============================================================================
// Login Screen
// ============================================================================

function Notice({ alert, onClose }) {
  const Icon = alert.type === "error"
    ? AlertTriangle
    : alert.type === "success"
      ? CheckCircle2
      : Info;

  return (
    <div className={`notice alert-${alert.type}`} role="status" aria-live="polite">
      <Icon size={20} />
      <div>{alert.message}</div>
      <button className="notice-close" type="button" aria-label="Fechar alerta" onClick={onClose}>
        <X size={18} />
      </button>
    </div>
  );
}

function LoginScreen({ onLogin, loading }) {
  const [email, setEmail] = useState("pedro@chegae.dev");

  function handleSubmit(e) {
    e.preventDefault();
    if (email.trim()) onLogin(email.trim());
  }

  return (
    <main className="app-shell" style={{ justifyContent: "center", alignItems: "center" }}>
      <section className="login-card">
        <div className="brand" style={{ marginBottom: "1.5rem" }}>
          <span className="brand-mark" aria-hidden="true">Cê</span>
          <div>
            <strong>ChegAê</strong>
            <small>Companhia para sair de casa</small>
          </div>
        </div>

        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <label>
            E-mail
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="seu@email.com"
              required
            />
          </label>

          <div style={{ fontSize: "0.8rem", opacity: 0.7, lineHeight: 1.4 }}>
            <strong>Contas demo:</strong><br />
            pedro@chegae.dev (Embaixador, reputação 4.9)<br />
            caio@chegae.dev (Embaixador, reputação 4.9)<br />
            marcos@chegae.dev (Embaixador, reputação 5.0)<br />
            baixa@chegae.dev (Reputação baixa, 2.5)<br />
            moderador@chegae.dev (Administrador)
          </div>

          <button className="primary-action" type="submit" disabled={loading}>
            <LogIn size={18} />
            {loading ? "Entrando..." : "Entrar"}
          </button>
        </form>
      </section>
    </main>
  );
}

// ============================================================================
// Sidebar
// ============================================================================

function Sidebar({ view, filters, setView, updateFilter, currentUser, onLogout }) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">Cê</span>
        <div>
          <strong>ChegAê</strong>
          <small>Companhia para sair de casa</small>
        </div>
      </div>

      <section className="profile-summary" aria-label="Resumo do perfil">
        <div>
          <span className="avatar" aria-hidden="true">{currentUser.name[0]}</span>
          <div>
            <strong>{currentUser.name}</strong>
            <small>{isAmbassador(currentUser.honor) ? "Embaixador" : "Participante"}</small>
          </div>
        </div>
        <div className="honor-meter">
          <span>Reputação</span>
          <strong>{currentUser.honor.toFixed(1)}</strong>
        </div>
      </section>

      <nav className="nav-tabs" aria-label="Navegação principal">
        {[
          ["feed", "Feed"],
          ["create", "Criar rolé"],
          ["profile", "Meu perfil"],
          ...(currentUser.isModerator ? [["moderation", "Moderação"]] : []),
        ].map(([key, label]) => (
          <button className={`nav-tab ${view === key ? "active" : ""}`} key={key} type="button" onClick={() => setView(key)}>
            {label}
          </button>
        ))}
        <button className="nav-tab" type="button" onClick={onLogout} style={{ marginTop: "auto", opacity: 0.7 }}>
          Sair
        </button>
      </nav>

      <section className="filters" aria-label="Filtros de eventos">
        <label>
          Buscar rolé
          <span className="input-with-icon">
            <Search size={16} />
            <input value={filters.search} type="search" placeholder="futebol, pets, patins..." onChange={(event) => updateFilter("search", event.target.value)} />
          </span>
        </label>

        <SelectFilter label="Categoria" value={filters.category} onChange={(value) => updateFilter("category", value)}>
          <option value="todos">Todas</option>
          {categories.map((category) => <option key={category} value={category}>{category}</option>)}
        </SelectFilter>

        <SelectFilter label="Distância" value={filters.distance} onChange={(value) => updateFilter("distance", value)}>
          <option value="todos">Qualquer distância</option>
          <option value="5">Até 5 km</option>
          <option value="15">Até 15 km</option>
          <option value="30">Até 30 km</option>
        </SelectFilter>

        <SelectFilter label="Honra do organizador" value={filters.honor} onChange={(value) => updateFilter("honor", value)}>
          <option value="0">Qualquer honra</option>
          <option value="4">4.0+</option>
          <option value="4.8">Embaixadores</option>
        </SelectFilter>
      </section>
    </aside>
  );
}

function SelectFilter({ label, value, onChange, children }) {
  return (
    <label>
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {children}
      </select>
    </label>
  );
}

// ============================================================================
// Feed View
// ============================================================================

function FeedView({ events, metrics, filters, enrollments, currentUser, resetFilters, openEvent, toggleEnrollment, deleteEvent }) {
  return (
    <section className="view" aria-label="Feed de eventos">
      <div className="mission-panel">
        <div>
          <span className="status-pill">Sobre o ChegAê</span>
          <h2>Conectando pessoas para atividades presenciais com segurança.</h2>
          <p>Nossa plataforma prioriza organizadores bem avaliados, gerencia participantes por lista de espera e centraliza os combinados do grupo.</p>
        </div>
        <div className="map-sketch" aria-hidden="true">
          <span className="route" />
          <span className="pin pin-a" />
          <span className="pin pin-b" />
          <span className="pin pin-c" />
          <strong>RJ</strong>
        </div>
      </div>

      <div className="metric-row">
        <Metric icon={<CalendarDays size={18} />} label="Eventos ativos" value={metrics.active} />
        <Metric icon={<UsersRound size={18} />} label="Na lista de espera" value={metrics.waitlist} />
        <Metric icon={<Star size={18} />} label="Embaixadores" value={metrics.ambassadors} />
      </div>

      <div className="section-heading">
        <div>
          <h2>Rolés disponíveis</h2>
          <span>{buildFilterSummary(filters)}</span>
        </div>
        <button className="secondary-action" type="button" onClick={resetFilters}>
          <X size={16} />
          Limpar filtros
        </button>
      </div>

      <div className="event-grid">
        {events.length ? (
          events.map((event) => (
            <EventCard
              key={event.id}
              event={event}
              enrollment={enrollments[event.id]}
              currentUser={currentUser}
              openEvent={openEvent}
              toggleEnrollment={toggleEnrollment}
              deleteEvent={deleteEvent}
            />
          ))
        ) : (
          <div className="empty-state">Nenhum rolé encontrado com os filtros atuais.</div>
        )}
      </div>
    </section>
  );
}

function Metric({ icon, label, value }) {
  return (
    <article>
      <span>{icon}{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

// ============================================================================
// Event Card
// ============================================================================

function EventCard({ event, enrollment, currentUser, openEvent, toggleEnrollment, deleteEvent }) {
  const seats = Math.max(event.capacity - event.confirmed.length, 0);
  const percent = Math.min((event.confirmed.length / event.capacity) * 100, 100);
  const featured = isAmbassador(event.organizer.honor);
  const locked = event.status !== "ABERTO";
  const canManage = currentUser?.isModerator || event.organizer.id === currentUser?.id;

  return (
    <article className={`event-card ${featured ? "featured" : ""}`}>
      <div className="event-card-header">
        <div>
          <span className="event-type">{event.category}</span>
          <h3>{event.title}</h3>
        </div>
        <EnrollmentBadge event={event} enrollment={enrollment} />
      </div>

      <div className="event-meta">
        <span>{formatDate(event.date)} às {event.time}</span>
        <span>{event.place}{event.distance > 0 ? ` - ${event.distance} km` : ""}</span>
        {event.confirmed.length >= event.capacity ? (
          <div style={{ marginTop: "6px" }}>
            <span style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              color: "var(--amber)",
              background: "rgba(217, 146, 32, 0.1)",
              padding: "4px 10px",
              borderRadius: "6px",
              fontSize: "0.82rem",
              fontWeight: "600"
            }}>
              <UsersRound size={14} /> Lista de espera ativa: {event.waitlist.length} {event.waitlist.length === 1 ? "pessoa" : "pessoas"} na fila
            </span>
          </div>
        ) : (
          <span>{event.confirmed.length}/{event.capacity} confirmados · {seats} vaga(s) restante(s)</span>
        )}
      </div>

      <div className="progress-track" aria-hidden="true">
        <span className="progress-fill" style={{ width: `${percent}%` }} />
      </div>

      <p>{event.description}</p>

      <div className="organizer-line">
        <span>Organizador: <strong>{event.organizer.name}</strong></span>
        <span>Reputação {event.organizer.honor.toFixed(1)}{featured ? " · Embaixador" : ""}</span>
      </div>

      <div className="card-actions">
        <button className="secondary-action" type="button" onClick={() => openEvent(event.id)}>Detalhes</button>
        <button className={locked ? "secondary-action" : "primary-action"} type="button" disabled={locked} onClick={() => toggleEnrollment(event.id)}>
          {getEnrollmentActionLabel(event, enrollment)}
        </button>
        {canManage && (
          <button className="danger-action" type="button" onClick={() => deleteEvent(event.id)}>
            <Trash2 size={16} />
            Excluir
          </button>
        )}
      </div>
    </article>
  );
}

function EnrollmentBadge({ event, enrollment }) {
  if (event.status === "TRAVADO") return <span className="enrollment-badge locked">Suspenso</span>;
  if (event.status === "CANCELADO") return <span className="enrollment-badge locked">Cancelado</span>;
  if (enrollment === "CONFIRMADO") return <span className="enrollment-badge confirmed">Confirmado</span>;
  if (enrollment === "FILA") return <span className="enrollment-badge waiting">Lista de Espera</span>;
  if (event.confirmed.length >= event.capacity) return <span className="enrollment-badge waiting">Esgotado</span>;
  return <span className="enrollment-badge confirmed">Vagas Abertas</span>;
}

// ============================================================================
// Create View
// ============================================================================

function CreateView({ categoryOptions, capacityLimit, onSubmit }) {
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
          <label>Limite de participantes<input name="capacity" required min="3" max={capacityLimit} type="number" defaultValue="20" /></label>
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

// ============================================================================
// Profile View
// ============================================================================

function ProfileView({ events, enrollments, currentUser }) {
  const myEnrollments = Object.entries(enrollments)
    .map(([eventId, status]) => ({ event: events.find((item) => item.id === eventId), status }))
    .filter((item) => item.event);

  return (
    <section className="view" aria-label="Perfil e honra">
      <div className="profile-grid">
        <article className="profile-panel">
          <span className="status-pill">Sua Reputação</span>
          <h2>{currentUser.fullName}</h2>
          <div className="big-honor">
            <strong>{currentUser.honor.toFixed(1)}</strong>
            <span>reputação média</span>
          </div>
          <p>{isAmbassador(currentUser.honor) 
            ? "Tag Embaixador ativa. Eventos criados por você ganham destaque e podem ter limite ampliado de vagas."
            : "Participante ativo. Aumente sua reputação para se tornar Embaixador."
          }</p>
        </article>

        <article className="profile-panel">
          <span className="status-pill">Minhas inscrições</span>
          <h2>Confirmados e lista de espera</h2>
          <div className="pending-list">
            {myEnrollments.length ? (
              myEnrollments.map(({ event, status }) => <StackItem key={event.id} title={event.title} body={`${status} - ${formatDate(event.date)} às ${event.time}`} />)
            ) : (
              <StackItem title="Sem inscrições" body="Quando você participar de um rolé, ele aparecerá aqui." />
            )}
          </div>
        </article>
      </div>
    </section>
  );
}

// ============================================================================
// Moderation View
// ============================================================================

function ModerationView({ reports, resolveReport }) {
  const pendingReports = reports.filter((report) => report.status === "PENDENTE");

  return (
    <section className="view" aria-label="Painel de moderação">
      <div className="section-heading">
        <div>
          <span className="status-pill">Controle de Moderação</span>
          <h2>Denúncias e travas</h2>
        </div>
      </div>
      <div className="moderation-grid">
        <section>
          <h3>Denúncias pendentes</h3>
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
          <h3>Categorias ativas</h3>
          <div className="stack-list">
            {categories.map((category) => <StackItem key={category} title={category} body="Categoria ativa no feed." />)}
          </div>
        </section>
      </div>
    </section>
  );
}

// ============================================================================
// Event Modal
// ============================================================================

function EventModal({ event, enrollment, close, toggleEnrollment, addComment, deleteEvent, reportEvent, currentUser }) {
  const [comment, setComment] = useState("");
  const canComment = enrollment === "CONFIRMADO" || enrollment === "FILA";
  const canManage = currentUser?.isModerator || event.organizer.id === currentUser?.id;

  function submitComment(formEvent) {
    formEvent.preventDefault();
    addComment(event.id, comment);
    setComment("");
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="event-dialog" role="dialog" aria-modal="true" aria-label={event.title}>
        <button className="icon-button" type="button" aria-label="Fechar" onClick={close}><X size={18} /></button>
        <div className="dialog-body">
          <div className="dialog-title">
            <span className="event-type">{event.category}</span>
            <h2>{event.title}</h2>
          </div>
          <p>{event.description}</p>

          <div className="dialog-stats">
            <div><strong>{formatDate(event.date)}</strong><span>{event.time}</span></div>
            <div><strong>{event.confirmed.length}/{event.capacity}</strong><span>confirmados</span></div>
            <div><strong>{event.waitlist.length}</strong><span>na lista de espera</span></div>
          </div>

          <div className="organizer-line">
            <span><MapPin size={15} /> {event.place} - CEP {event.cep || "não informado"}</span>
            <span>Reputação {event.organizer.honor.toFixed(1)}</span>
          </div>

          <div className="card-actions">
            <button className="primary-action" type="button" disabled={event.status !== "ABERTO"} onClick={() => toggleEnrollment(event.id)}>
              {getEnrollmentActionLabel(event, enrollment)}
            </button>
            <button className="secondary-action" type="button" onClick={() => reportEvent(event.id)}>
              <Flag size={16} />
              Denunciar
            </button>
            {canManage && (
              <button className="danger-action" type="button" onClick={() => deleteEvent(event.id)}>
                <Trash2 size={16} />
                Excluir
              </button>
            )}
          </div>

          <section className="comments-box">
            <h3>Comentários do rolé</h3>
            {event.comments.length ? (
              event.comments.map((item, index) => (
                <article className="comment-item" key={`${item.author}-${item.time}-${index}`}>
                  <header><strong>{item.author}</strong><span className="muted">{item.time}</span></header>
                  <p>{item.text}</p>
                </article>
              ))
            ) : (
              <div className="empty-state">Sem comentários ainda.</div>
            )}

            {canComment ? (
              <form className="comment-form" onSubmit={submitComment}>
                <label>
                  Novo comentário
                  <textarea value={comment} onChange={(event) => setComment(event.target.value)} required maxLength="180" rows="3" placeholder="Combine detalhes com o grupo." />
                </label>
                <button className="secondary-action" type="submit">Publicar comentário</button>
              </form>
            ) : (
              <div className="empty-state">Apenas participantes confirmados ou na lista de espera podem comentar.</div>
            )}
          </section>
        </div>
      </section>
    </div>
  );
}

// ============================================================================
// Shared Components
// ============================================================================

function StackItem({ title, body }) {
  return (
    <article className="stack-item">
      <strong>{title}</strong>
      <p>{body}</p>
    </article>
  );
}

// ============================================================================
// Utility Functions
// ============================================================================

function getEnrollmentActionLabel(event, enrollment) {
  if (event.status === "TRAVADO") return "Suspenso";
  if (event.status === "CANCELADO") return "Cancelado";
  if (enrollment === "CONFIRMADO") return "Cancelar presença";
  if (enrollment === "FILA") return "Sair da lista de espera";
  return event.confirmed.length >= event.capacity ? "Entrar na lista de espera" : "Quero participar";
}

function buildFilterSummary(filters) {
  const parts = [];
  if (filters.category !== "todos") parts.push(filters.category);
  if (filters.distance !== "todos") parts.push(`até ${filters.distance} km`);
  if (filters.honor !== "0") parts.push(`honra ${filters.honor}+`);
  if (filters.search) parts.push(`busca "${filters.search}"`);
  return parts.length ? parts.join(" · ") : "Todos os rolés";
}

function isAmbassador(honor) {
  return honor >= 4.8;
}

function createId() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return `id-${Date.now()}-${Math.round(Math.random() * 10000)}`;
}

function formatDate(date) {
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date(`${date}T12:00:00`));
}
