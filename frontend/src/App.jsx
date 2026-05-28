import { CalendarDays, Flag, MapPin, Plus, Search, Star, UsersRound, X } from "lucide-react";
import React, { useEffect, useMemo, useState } from "react";

const STORAGE_KEY = "chegaae.events.v2";
const ENROLLMENT_KEY = "chegaae.enrollments.v2";
const REPORT_KEY = "chegaae.reports.v2";

const categories = ["Futebol", "Pets", "Patinação", "Passeio", "Esporte", "Cultura", "Lazer"];

const currentUser = {
  id: "user-pedro",
  name: "Pedro",
  fullName: "Pedro Farias",
  honor: 4.9,
  pendingEvaluations: [],
  isModerator: true,
};

const seedEvents = [
  {
    id: "evt-fut-1",
    title: "Futebol no Aterro",
    category: "Futebol",
    date: "2026-05-02",
    time: "09:00",
    cep: "22250-040",
    place: "Aterro do Flamengo, Rio de Janeiro",
    distance: 6,
    capacity: 20,
    status: "ABERTO",
    organizer: { id: "user-caio", name: "Caio", honor: 4.9 },
    confirmed: ["Caio", "Marcos", "Lia", "Nina", "Rafael", "Bia", "Davi", "João", "Iris", "Ana", "Theo", "Luiza"],
    waitlist: [],
    comments: [
      { author: "Marcos", text: "Vou levar duas bolas e coletes.", time: "08:20" },
      { author: "Lia", text: "O ponto de encontro é perto da quadra principal?", time: "09:05" },
    ],
    reports: 0,
    description: "Pelada leve com times mistos. Chegar 15 minutos antes para dividir equipes e combinar regras.",
  },
  {
    id: "evt-pets-1",
    title: "Passeio com cães na praça",
    category: "Pets",
    date: "2026-05-03",
    time: "16:30",
    cep: "22290-240",
    place: "Praça Nelson Mandela, Botafogo",
    distance: 3,
    capacity: 8,
    status: "ABERTO",
    organizer: { id: "user-nina", name: "Nina", honor: 4.7 },
    confirmed: ["Nina", "Paula", "Bruno", "Lara", "Gabi", "Ruan", "Sofia", "Léo"],
    waitlist: ["Mateus", "Helena"],
    comments: [{ author: "Paula", text: "Meu cachorro é pequeno e fica bem com outros cães.", time: "12:12" }],
    reports: 0,
    description: "Rolé tranquilo para socializar pets. Guia obrigatória e água para os cães.",
  },
  {
    id: "evt-patins-1",
    title: "Patinação iniciante na orla",
    category: "Patinação",
    date: "2026-05-04",
    time: "18:00",
    cep: "22010-000",
    place: "Copacabana, Rio de Janeiro",
    distance: 11,
    capacity: 50,
    status: "ABERTO",
    organizer: { id: "user-marcos", name: "Marcos", honor: 4.95 },
    confirmed: ["Marcos", "Breno", "Clara", "Vitor", "Duda", "Malu", "Enzo", "Tainá", "Júlia", "Igor", "Ceci", "Rita", "Noah"],
    waitlist: [],
    comments: [{ author: "Clara", text: "Posso ir mesmo sabendo frear só um pouco?", time: "15:44" }],
    reports: 0,
    description: "Treino básico para quem quer ganhar confiança. Embaixador organizando, capacete recomendado.",
  },
  {
    id: "evt-lazer-1",
    title: "Café e jogo de tabuleiro",
    category: "Lazer",
    date: "2026-05-05",
    time: "15:00",
    cep: "20550-013",
    place: "Tijuca, Rio de Janeiro",
    distance: 18,
    capacity: 6,
    status: "TRAVADO",
    organizer: { id: "user-ivan", name: "Ivan", honor: 3.4 },
    confirmed: ["Ivan", "Mia", "Tom", "Rafa"],
    waitlist: [],
    comments: [],
    reports: 2,
    description: "Encontro em local público. Evento temporariamente travado por análise de moderação.",
  },
];

const seedReports = [
  {
    id: "rep-1",
    target: "Café e jogo de tabuleiro",
    author: "Mia",
    reason: "Local precisa ser confirmado antes de liberar novas inscrições.",
    status: "PENDENTE",
  },
];

const initialFilters = {
  search: "",
  category: "todos",
  distance: "todos",
  honor: "0",
};

export default function App() {
  const [events, setEvents] = useStoredState(STORAGE_KEY, seedEvents);
  const [enrollments, setEnrollments] = useStoredState(ENROLLMENT_KEY, {});
  const [reports, setReports] = useStoredState(REPORT_KEY, seedReports);
  const [view, setView] = useState("feed");
  const [filters, setFilters] = useState(initialFilters);
  const [selectedEventId, setSelectedEventId] = useState(null);
  const [notice, setNotice] = useState("");

  const capacityLimit = isAmbassador(currentUser.honor) ? 50 : 20;
  const selectedEvent = events.find((event) => event.id === selectedEventId);

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

  function createEvent(formEvent) {
    formEvent.preventDefault();

    if (currentUser.honor < 2) {
      showNotice("Sua honra está abaixo de 2.0. Pelo RN004, você pode participar, mas não criar novos rolés.");
      return;
    }

    if (currentUser.pendingEvaluations.length > 0) {
      showNotice("Você possui avaliações pendentes. Pelo RN007, conclua as avaliações antes de criar ou entrar em novos rolés.");
      return;
    }

    const form = new FormData(formEvent.currentTarget);
    const event = {
      id: createId(),
      title: form.get("title").trim(),
      category: form.get("category"),
      date: form.get("date"),
      time: form.get("time"),
      cep: form.get("cep").trim(),
      place: form.get("place").trim(),
      distance: Number(form.get("distance")),
      capacity: Math.min(Number(form.get("capacity")), capacityLimit),
      status: "ABERTO",
      organizer: { id: currentUser.id, name: currentUser.name, honor: currentUser.honor },
      confirmed: [currentUser.name],
      waitlist: [],
      comments: [{ author: currentUser.name, text: "Rolé publicado. Confirmem presença por aqui.", time: getTime() }],
      reports: 0,
      description: form.get("description").trim(),
    };

    setEvents((current) => [event, ...current]);
    setEnrollments((current) => ({ ...current, [event.id]: "CONFIRMADO" }));
    formEvent.currentTarget.reset();
    setView("feed");
    setSelectedEventId(event.id);
  }

  function toggleEnrollment(eventId) {
    if (currentUser.pendingEvaluations.length > 0) {
      showNotice("Você possui avaliações pendentes. Pelo RN007, conclua essas avaliações antes de entrar em outro rolé.");
      return;
    }

    const event = events.find((item) => item.id === eventId);
    if (!event || event.status !== "ABERTO") return;

    const enrollment = enrollments[eventId];

    setEvents((currentEvents) =>
      currentEvents.map((item) => {
        if (item.id !== eventId) return item;
        const confirmed = item.confirmed.filter((name) => name !== currentUser.name);
        const waitlist = item.waitlist.filter((name) => name !== currentUser.name);

        if (enrollment) {
          const promoted = confirmed.length < item.capacity ? waitlist.shift() : null;
          return { ...item, confirmed: promoted ? [...confirmed, promoted] : confirmed, waitlist };
        }

        if (item.confirmed.length < item.capacity) {
          return { ...item, confirmed: [...item.confirmed, currentUser.name] };
        }

        return { ...item, waitlist: [...item.waitlist, currentUser.name] };
      })
    );

    setEnrollments((current) => {
      const next = { ...current };
      if (enrollment) {
        delete next[eventId];
      } else {
        next[eventId] = event.confirmed.length < event.capacity ? "CONFIRMADO" : "FILA";
      }
      return next;
    });
  }

  function addComment(eventId, text) {
    if (!text.trim()) return;
    setEvents((current) =>
      current.map((event) =>
        event.id === eventId
          ? { ...event, comments: [...event.comments, { author: currentUser.name, text: text.trim(), time: getTime() }] }
          : event
      )
    );
  }

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
    setEvents((current) =>
      current.map((item) =>
        item.id === eventId
          ? { ...item, reports: item.reports + 1, status: item.reports + 1 >= 2 ? "TRAVADO" : item.status }
          : item
      )
    );
  }

  function resolveReport(reportId, shouldLockEvent) {
    const report = reports.find((item) => item.id === reportId);
    if (!report) return;

    setReports((current) =>
      current.map((item) => (item.id === reportId ? { ...item, status: shouldLockEvent ? "PROCEDENTE" : "IMPROCEDENTE" } : item))
    );

    if (shouldLockEvent) {
      setEvents((current) => current.map((event) => (event.title === report.target ? { ...event, status: "TRAVADO" } : event)));
    }
  }

  function showNotice(message) {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 4600);
  }

  return (
    <main className="app-shell">
      <Sidebar view={view} filters={filters} setView={setView} updateFilter={updateFilter} />

      <section className="content">
        <header className="topbar">
          <div>
            <p className="eyebrow">SPA responsiva - protótipo B4</p>
            <h1>Encontre companhia confiável para rolés presenciais.</h1>
          </div>
          <button className="primary-action" type="button" onClick={() => setView("create")}>
            <Plus size={18} />
            Criar rolé
          </button>
        </header>

        {notice && <div className="notice">{notice}</div>}

        {view === "feed" && (
          <FeedView
            events={filteredEvents}
            metrics={metrics}
            filters={filters}
            enrollments={enrollments}
            resetFilters={() => setFilters(initialFilters)}
            openEvent={setSelectedEventId}
            toggleEnrollment={toggleEnrollment}
          />
        )}

        {view === "create" && <CreateView categoryOptions={categories} capacityLimit={capacityLimit} onSubmit={createEvent} />}
        {view === "profile" && <ProfileView events={events} enrollments={enrollments} />}
        {view === "moderation" && <ModerationView reports={reports} resolveReport={resolveReport} />}
      </section>

      {selectedEvent && (
        <EventModal
          event={selectedEvent}
          enrollment={enrollments[selectedEvent.id]}
          close={() => setSelectedEventId(null)}
          toggleEnrollment={toggleEnrollment}
          addComment={addComment}
          reportEvent={reportEvent}
        />
      )}
    </main>
  );
}

function Sidebar({ view, filters, setView, updateFilter }) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">Cê</span>
        <div>
          <strong>ChegaAê</strong>
          <small>Companhia para sair de casa</small>
        </div>
      </div>

      <section className="profile-summary" aria-label="Resumo do perfil">
        <div>
          <span className="avatar" aria-hidden="true">P</span>
          <div>
            <strong>{currentUser.name}</strong>
            <small>{isAmbassador(currentUser.honor) ? "Embaixador" : "Participante"}</small>
          </div>
        </div>
        <div className="honor-meter">
          <span>Honra</span>
          <strong>{currentUser.honor.toFixed(1)}</strong>
        </div>
      </section>

      <nav className="nav-tabs" aria-label="Navegação principal">
        {[
          ["feed", "Feed"],
          ["create", "Criar rolé"],
          ["profile", "Meu perfil"],
          ["moderation", "Moderação"],
        ].map(([key, label]) => (
          <button className={`nav-tab ${view === key ? "active" : ""}`} key={key} type="button" onClick={() => setView(key)}>
            {label}
          </button>
        ))}
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

function FeedView({ events, metrics, filters, enrollments, resetFilters, openEvent, toggleEnrollment }) {
  return (
    <section className="view" aria-label="Feed de eventos">
      <div className="mission-panel">
        <div>
          <span className="status-pill">Sistema ChegaAê</span>
          <h2>Eventos, fila, honra e moderação no mesmo fluxo.</h2>
          <p>O feed prioriza organizadores bem avaliados, controla vagas com fila FIFO e mantém comentários dentro da página do evento.</p>
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
        <Metric icon={<UsersRound size={18} />} label="Fila de espera" value={metrics.waitlist} />
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
            <EventCard key={event.id} event={event} enrollment={enrollments[event.id]} openEvent={openEvent} toggleEnrollment={toggleEnrollment} />
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

function EventCard({ event, enrollment, openEvent, toggleEnrollment }) {
  const seats = Math.max(event.capacity - event.confirmed.length, 0);
  const percent = Math.min((event.confirmed.length / event.capacity) * 100, 100);
  const featured = isAmbassador(event.organizer.honor);
  const locked = event.status !== "ABERTO";

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
        <span>{event.place} - {event.distance} km</span>
        <span>{event.confirmed.length}/{event.capacity} confirmados - {seats} vaga(s)</span>
      </div>

      <div className="progress-track" aria-hidden="true">
        <span className="progress-fill" style={{ width: `${percent}%` }} />
      </div>

      <p>{event.description}</p>

      <div className="organizer-line">
        <span>Organizador: <strong>{event.organizer.name}</strong></span>
        <span>{event.organizer.honor.toFixed(1)} honra{featured ? " · Embaixador" : ""}</span>
      </div>

      <div className="card-actions">
        <button className="secondary-action" type="button" onClick={() => openEvent(event.id)}>Detalhes</button>
        <button className={locked ? "secondary-action" : "primary-action"} type="button" disabled={locked} onClick={() => toggleEnrollment(event.id)}>
          {getEnrollmentActionLabel(event, enrollment)}
        </button>
      </div>
    </article>
  );
}

function EnrollmentBadge({ event, enrollment }) {
  if (event.status === "TRAVADO") return <span className="enrollment-badge locked">Travado</span>;
  if (event.status === "CANCELADO") return <span className="enrollment-badge locked">Cancelado</span>;
  if (enrollment === "CONFIRMADO") return <span className="enrollment-badge confirmed">Confirmado</span>;
  if (enrollment === "FILA") return <span className="enrollment-badge waiting">Fila</span>;
  if (event.confirmed.length >= event.capacity) return <span className="enrollment-badge waiting">Fila aberta</span>;
  return <span className="enrollment-badge confirmed">Aberto</span>;
}

function CreateView({ categoryOptions, capacityLimit, onSubmit }) {
  return (
    <section className="view" aria-label="Criar evento">
      <form className="event-form" onSubmit={onSubmit}>
        <div className="form-heading">
          <span className="status-pill">UC03 - Gerenciar eventos</span>
          <h2>Novo rolé</h2>
          <p>Como embaixador, você pode criar eventos com até {capacityLimit} vagas.</p>
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

function ProfileView({ events, enrollments }) {
  const myEnrollments = Object.entries(enrollments)
    .map(([eventId, status]) => ({ event: events.find((item) => item.id === eventId), status }))
    .filter((item) => item.event);

  return (
    <section className="view" aria-label="Perfil e honra">
      <div className="profile-grid">
        <article className="profile-panel">
          <span className="status-pill">UC10 - Perfil / Honra</span>
          <h2>{currentUser.fullName}</h2>
          <div className="big-honor">
            <strong>{currentUser.honor.toFixed(1)}</strong>
            <span>honra média</span>
          </div>
          <p>Tag Embaixador ativa. Eventos criados por você ganham destaque e podem ter limite ampliado de vagas.</p>
        </article>

        <article className="profile-panel">
          <span className="status-pill">RN007</span>
          <h2>Avaliações pendentes</h2>
          <div className="pending-list">
            {currentUser.pendingEvaluations.length ? (
              currentUser.pendingEvaluations.map((item) => <StackItem key={item} title={item} body="Avaliação obrigatória antes de nova inscrição." />)
            ) : (
              <StackItem title="Nada pendente" body="Você pode criar eventos e se inscrever em novos rolés." />
            )}
          </div>
        </article>

        <article className="profile-panel">
          <span className="status-pill">Minhas inscrições</span>
          <h2>Confirmados e fila</h2>
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

function ModerationView({ reports, resolveReport }) {
  const pendingReports = reports.filter((report) => report.status === "PENDENTE");

  return (
    <section className="view" aria-label="Painel de moderação">
      <div className="section-heading">
        <div>
          <span className="status-pill">UC13 - Painel do moderador</span>
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

function EventModal({ event, enrollment, close, toggleEnrollment, addComment, reportEvent }) {
  const [comment, setComment] = useState("");
  const canComment = enrollment === "CONFIRMADO" || enrollment === "FILA";

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
            <div><strong>{event.waitlist.length}</strong><span>na fila FIFO</span></div>
          </div>

          <div className="organizer-line">
            <span><MapPin size={15} /> {event.place} - CEP {event.cep || "não informado"}</span>
            <span>Honra {event.organizer.honor.toFixed(1)}</span>
          </div>

          <div className="card-actions">
            <button className="primary-action" type="button" disabled={event.status !== "ABERTO"} onClick={() => toggleEnrollment(event.id)}>
              {getEnrollmentActionLabel(event, enrollment)}
            </button>
            <button className="secondary-action" type="button" onClick={() => reportEvent(event.id)}>
              <Flag size={16} />
              Denunciar
            </button>
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
              <div className="empty-state">Entre no rolé ou na fila de espera para comentar, conforme RN006.</div>
            )}
          </section>
        </div>
      </section>
    </div>
  );
}

function StackItem({ title, body }) {
  return (
    <article className="stack-item">
      <strong>{title}</strong>
      <p>{body}</p>
    </article>
  );
}

function useStoredState(key, fallback) {
  const [value, setValue] = useState(() => {
    try {
      const stored = localStorage.getItem(key);
      return stored ? JSON.parse(stored) : fallback;
    } catch {
      return fallback;
    }
  });

  useEffect(() => {
    localStorage.setItem(key, JSON.stringify(value));
  }, [key, value]);

  return [value, setValue];
}

function getEnrollmentActionLabel(event, enrollment) {
  if (event.status === "TRAVADO") return "Travado";
  if (event.status === "CANCELADO") return "Cancelado";
  if (enrollment === "CONFIRMADO") return "Cancelar presença";
  if (enrollment === "FILA") return "Sair da fila";
  return event.confirmed.length >= event.capacity ? "Entrar na fila" : "Participar";
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
  return honor > 4.8;
}

function createId() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return `id-${Date.now()}-${Math.round(Math.random() * 10000)}`;
}

function getTime() {
  return new Intl.DateTimeFormat("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date());
}

function formatDate(date) {
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date(`${date}T12:00:00`));
}
