"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Notice from "./components/Notice";
import LoginScreen from "./components/LoginScreen";
import Sidebar from "./components/Sidebar";
import Brand from "./components/Brand";
import ThemeToggle from "./components/ThemeToggle";
import FeedView from "./components/FeedView";
import CreateView from "./components/CreateView";
import ProfileView from "./components/ProfileView";
import ModerationView from "./components/ModerationView";
import EventModal from "./components/EventModal";
import ConfirmDialog from "./components/ConfirmDialog";
import { api, cleanErrorMessage, TOKEN_KEY, USER_KEY, SESSION_EXPIRED_EVENT } from "./lib/api";
import { createSessionGuard, StaleSessionError, isDiscardedRequest } from "./lib/session";
import { categories, initialFilters, createId, sortByDate } from "./lib/eventos";
import { useDemora } from "./lib/useDemora";

const EMPTY_RATINGS = { podeAvaliar: false, eventos: [] };
const CREATION_BLOCKED = "Sua conta não pode criar rolés no momento. Confira sua reputação ou aguarde o fim da suspensão.";

export default function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const userRef = useRef(null);
  const [sessionVersion, setSessionVersion] = useState(0);
  const guardRef = useRef(null);
  if (!guardRef.current) guardRef.current = createSessionGuard(() => localStorage.getItem(TOKEN_KEY));
  const guard = guardRef.current;
  const [events, setEvents] = useState([]);
  const eventsRef = useRef([]);
  const [enrollments, setEnrollments] = useState({});
  const [enrollmentsReady, setEnrollmentsReady] = useState(false);
  const [ratings, setRatings] = useState(EMPTY_RATINGS);
  const [accountLoading, setAccountLoading] = useState(false);
  const [accountError, setAccountError] = useState(false);
  const [management, setManagement] = useState({});
  const [reports, setReports] = useState([]);
  const [view, setView] = useState("feed");
  const [filters, setFilters] = useState(initialFilters);
  const [selectedEventId, setSelectedEventId] = useState(null);
  const [ratingsEventId, setRatingsEventId] = useState(null);
  const [alert, setAlert] = useState(null);
  const [loadState, setLoadState] = useState("idle");
  const loadingSlow = useDemora(loadState === "loading");
  const [pending, setPending] = useState({});
  const pendingRef = useRef(new Map());
  const [confirmState, setConfirmState] = useState(null);
  const confirmRef = useRef(null);
  const eventRequestRef = useRef(0);
  const accountRequestRef = useRef(0);
  const managementRequestsRef = useRef(new Map());
  const loginRequestRef = useRef(0);
  const selectedEvent = events.find((event) => event.id === selectedEventId);

  function showNotice(message, type = "info", scope = guard.capture()) {
    if (!guard.isCurrent(scope)) return;
    setAlert({ message: type === "error" ? cleanErrorMessage(message) : message, type, id: createId() });
  }
  async function sessionApi(scope, path, options = {}) {
    if (!guard.isCurrent(scope)) throw new StaleSessionError();
    const result = await api(path, {
      ...options, token: scope.token, signal: scope.controller?.signal,
      isCurrentSession: () => guard.isCurrent(scope),
    });
    if (!guard.isCurrent(scope)) throw new StaleSessionError();
    return result;
  }
  function applyEvents(data) {
    eventsRef.current = data;
    setEvents(data);
  }
  function discardConfirmation() {
    confirmRef.current?.resolve(false);
    confirmRef.current = null;
    setConfirmState(null);
  }
  function replaceSession(token, user) {
    discardConfirmation();
    if (token) {
      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    } else {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    }
    const scope = guard.replace(token);
    userRef.current = user;
    setCurrentUser(user);
    setSessionVersion(scope.generation);
    pendingRef.current = new Map();
    setPending({});
    applyEvents([]);
    setEnrollments({});
    setEnrollmentsReady(false);
    setRatings(EMPTY_RATINGS);
    setManagement({});
    setReports([]);
    setSelectedEventId(null);
    setRatingsEventId(null);
    setLoadState("idle");
    setAccountLoading(false);
    setAccountError(false);
    setAlert(null);
    setView("feed");
    setFilters(initialFilters);
    return scope;
  }
  function handleLogout() {
    loginRequestRef.current += 1;
    replaceSession(null, null);
  }
  async function loadEvents(scope = guard.capture(), { initial = false } = {}) {
    if (!scope.token || !guard.isCurrent(scope)) return false;
    const request = ++eventRequestRef.current;
    if (initial) setLoadState("loading");
    try {
      const data = await sessionApi(scope, "/eventos");
      if (request !== eventRequestRef.current) return false;
      applyEvents(data);
      setLoadState("ready");
      return true;
    } catch (error) {
      if (!guard.isCurrent(scope) || request !== eventRequestRef.current || isDiscardedRequest(error)) return false;
      if (initial) {
        setLoadState("error");
        showNotice("Não foi possível carregar os rolés. Verifique sua conexão e tente de novo.", "error", scope);
      }
      return false;
    }
  }
  async function refreshAccount(scope = guard.capture(), { reportError = false } = {}) {
    if (!scope.token || !guard.isCurrent(scope)) return null;
    const request = ++accountRequestRef.current;
    setAccountLoading(true);
    const results = await Promise.allSettled([
      sessionApi(scope, "/auth/me"),
      sessionApi(scope, "/auth/me/inscricoes"),
      sessionApi(scope, "/auth/me/avaliacoes-pendentes"),
    ]);
    if (!guard.isCurrent(scope) || request !== accountRequestRef.current) return null;
    const [profile, registrations, pendingRatings] = results;
    if (profile.status === "fulfilled") {
      userRef.current = profile.value;
      setCurrentUser(profile.value);
      localStorage.setItem(USER_KEY, JSON.stringify(profile.value));
    }
    if (registrations.status === "fulfilled") {
      setEnrollments(Object.fromEntries(registrations.value.map((item) => [item.eventoId, item.status])));
      setEnrollmentsReady(true);
    }
    if (pendingRatings.status === "fulfilled") setRatings(pendingRatings.value);
    const failed = results.some((result) => result.status === "rejected");
    setAccountError(failed);
    setAccountLoading(false);
    if (failed && reportError) showNotice("Não foi possível atualizar todos os dados da sua conta. Tente novamente.", "error", scope);
    return profile.status === "fulfilled" ? profile.value : null;
  }
  async function loadManagement(eventId, scope = guard.capture()) {
    const event = eventsRef.current.find((item) => item.id === eventId);
    if (!guard.isCurrent(scope) || event?.organizer.id !== userRef.current?.id) return false;
    const request = (managementRequestsRef.current.get(eventId) || 0) + 1;
    managementRequestsRef.current.set(eventId, request);
    setManagement((current) => guard.isCurrent(scope) ? { ...current, [eventId]: { ...current[eventId], state: "loading" } } : current);
    try {
      const data = await sessionApi(scope, `/eventos/${eventId}/participantes`);
      if (managementRequestsRef.current.get(eventId) !== request) return false;
      setManagement((current) => guard.isCurrent(scope) ? { ...current, [eventId]: { state: "ready", data } } : current);
      return true;
    } catch (error) {
      if (!guard.isCurrent(scope) || isDiscardedRequest(error) || managementRequestsRef.current.get(eventId) !== request) return false;
      setManagement((current) => guard.isCurrent(scope) ? { ...current, [eventId]: { state: "error" } } : current);
      return false;
    }
  }
  async function refreshAfterAction(scope) {
    const [feed] = await Promise.all([loadEvents(scope), refreshAccount(scope, { reportError: true })]);
    return feed;
  }
  useEffect(() => {
    api("/actuator/health", { semToken: true }).catch(() => {});
    const token = localStorage.getItem(TOKEN_KEY);
    const savedUser = localStorage.getItem(USER_KEY);
    try {
      const user = savedUser ? JSON.parse(savedUser) : null;
      replaceSession(token && user?.id ? token : null, token && user?.id ? user : null);
    } catch {
      replaceSession(null, null);
    }
    return () => {
      guard.dispose();
      confirmRef.current?.resolve(false);
    };
  }, []);
  // Esta chave muda só ao entrar/sair. Atualizar o objeto do perfil não recarrega o feed em ciclo.
  useEffect(() => {
    const scope = guard.capture();
    if (!scope.token) return;
    loadEvents(scope, { initial: true });
    refreshAccount(scope, { reportError: true });
    const refresh = () => {
      if (document.visibilityState === "visible" && guard.isCurrent(scope)) {
        loadEvents(scope);
        refreshAccount(scope);
      }
    };
    const expired = (event) => {
      if (guard.isCurrent(scope) && event.detail?.token === scope.token) {
        handleLogout();
        showNotice("Sua sessão expirou. Entre novamente.", "error");
      }
    };
    const storage = (event) => {
      if (event.key === TOKEN_KEY && localStorage.getItem(TOKEN_KEY) !== scope.token) {
        try {
          const token = localStorage.getItem(TOKEN_KEY);
          const saved = JSON.parse(localStorage.getItem(USER_KEY) || "null");
          replaceSession(token && saved?.id ? token : null, token && saved?.id ? saved : null);
        } catch {
          replaceSession(null, null);
        }
      }
    };
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    window.addEventListener("storage", storage);
    return () => {
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
      window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
      window.removeEventListener("storage", storage);
    };
  }, [sessionVersion]);
  useEffect(() => {
    if (selectedEventId) loadManagement(selectedEventId);
  }, [selectedEventId, sessionVersion]);
  useEffect(() => {
    if (alert?.type !== "success") return;
    const timer = setTimeout(() => setAlert((current) => current?.id === alert.id ? null : current), 6000);
    return () => clearTimeout(timer);
  }, [alert]);
  const filteredEvents = useMemo(() => sortByDate(events.filter((event) => {
    const text = `${event.title} ${event.category} ${event.place} ${event.description} ${event.organizer.name}`.toLowerCase();
    const matchesHonor = filters.honor === "ambassador" ? event.organizer.isAmbassador : event.organizer.honor >= Number(filters.honor);
    return (filters.category === "todos" || event.category === filters.category) && matchesHonor && text.includes(filters.search.toLowerCase());
  })), [events, filters]);
  const metrics = useMemo(() => ({
    active: events.filter((event) => event.status === "ABERTO").length,
    waitlist: events.reduce((total, event) => total + event.waitlist.length, 0),
    ambassadors: events.filter((event) => event.organizer.isAmbassador).length,
  }), [events]);
  async function runOnce(key, action, scope = guard.capture()) {
    if (!guard.isCurrent(scope) || pendingRef.current.has(key)) return false;
    const entry = { scope };
    pendingRef.current.set(key, entry);
    setPending((current) => guard.isCurrent(scope) ? { ...current, [key]: true } : current);
    try {
      const result = await action(scope);
      return guard.isCurrent(scope) ? result : false;
    } catch (error) {
      if (!guard.isCurrent(scope) || isDiscardedRequest(error)) return false;
      showNotice(error.message, "error", scope);
      return false;
    } finally {
      // Um finally antigo nunca remove o pending de uma ação iniciada pela nova sessão.
      if (guard.isCurrent(scope) && pendingRef.current.get(key) === entry) {
        pendingRef.current.delete(key);
        setPending((current) => {
          if (!guard.isCurrent(scope)) return current;
          const next = { ...current };
          delete next[key];
          return next;
        });
      }
    }
  }
  function askConfirm(options, scope) {
    discardConfirmation();
    if (!guard.isCurrent(scope)) return Promise.resolve(false);
    return new Promise((resolve) => {
      const confirmation = { ...options, scope, resolve };
      confirmRef.current = confirmation;
      setConfirmState(confirmation);
    });
  }
  function settleConfirm(answer) {
    const confirmation = confirmRef.current;
    confirmRef.current = null;
    setConfirmState(null);
    confirmation?.resolve(answer && guard.isCurrent(confirmation.scope));
  }
  async function authenticate(path, body, welcome) {
    const scope = guard.capture();
    const attempt = ++loginRequestRef.current;
    try {
      const result = await sessionApi(scope, path, { semToken: true, method: "POST", body: JSON.stringify(body) });
      if (attempt !== loginRequestRef.current) return;
      const active = replaceSession(result.token, result.user);
      showNotice(welcome(result.user), "success", active);
    } catch (error) {
      if (attempt !== loginRequestRef.current || !guard.isCurrent(scope) || isDiscardedRequest(error)) return;
      throw error;
    }
  }
  function handleLogin(email, password) {
    return authenticate("/auth/login", { email, password }, (user) => `Bem-vindo(a), ${user.name}!`);
  }
  function handleRegister(account) {
    return authenticate("/auth/cadastro", account, (user) => `Conta criada! Bem-vindo(a), ${user.name}!`);
  }
  async function handleSetView(nextView) {
    const scope = guard.capture();
    if (nextView === "create") {
      const profile = await refreshAccount(scope, { reportError: true });
      if (!guard.isCurrent(scope) || !profile) return;
      if (!profile.canCreateEvents) return showNotice(CREATION_BLOCKED, "error", scope);
    }
    if (!guard.isCurrent(scope)) return;
    setView(nextView);
    if (nextView === "profile") refreshAccount(scope, { reportError: true });
  }
  function openRatings(eventId) {
    setSelectedEventId(null);
    setRatingsEventId(eventId);
    setView("profile");
    refreshAccount(guard.capture(), { reportError: true });
  }
  async function createEvent(formEvent) {
    formEvent.preventDefault();
    const formElement = formEvent.currentTarget;
    const form = new FormData(formElement);
    await runOnce("criacao", async (scope) => {
      const profile = await refreshAccount(scope, { reportError: true });
      if (!guard.isCurrent(scope) || !profile) return false;
      if (!profile.canCreateEvents) return showNotice(CREATION_BLOCKED, "error", scope);
      const dto = {
        title: form.get("title").trim(), category: form.get("category"), date: form.get("date"), time: form.get("time"),
        cep: form.get("cep")?.trim() || "", place: form.get("place").trim(),
        capacity: Math.min(Number(form.get("capacity")), profile.capacityLimit),
        distance: Number(form.get("distance")), description: form.get("description").trim(),
      };
      const created = await sessionApi(scope, "/eventos", { method: "POST", body: JSON.stringify(dto) });
      eventRequestRef.current += 1;
      applyEvents([...eventsRef.current.filter((item) => item.id !== created.id), created]);
      setEnrollments((current) => guard.isCurrent(scope) ? { ...current, [created.id]: "CONFIRMADO" } : current);
      formElement.reset();
      setView("feed");
      setSelectedEventId(created.id);
      showNotice("Rolé criado com sucesso!", "success", scope);
      if (!(await refreshAfterAction(scope))) showNotice("Rolé criado, mas a lista não pôde ser atualizada. Tente novamente.", "info", scope);
      return true;
    });
  }
  async function toggleEnrollment(eventId) {
    const scope = guard.capture();
    if (!enrollmentsReady || pendingRef.current.has(`inscricao:${eventId}`)) return;
    const event = eventsRef.current.find((item) => item.id === eventId);
    if (!event || event.organizer.id === userRef.current?.id) return;
    const enrollment = enrollments[eventId];
    if (enrollment === "CONFIRMADO" || enrollment === "FILA") {
      const confirmed = await askConfirm({
        title: enrollment === "CONFIRMADO" ? "Cancelar sua presença?" : "Sair da lista de espera?",
        message: enrollment === "CONFIRMADO"
          ? `Sua vaga em "${event.title}" pode ir para a próxima pessoa da fila, sem garantia de recuperá-la.`
          : `Você perde seu lugar na fila de "${event.title}".`,
        confirmLabel: enrollment === "CONFIRMADO" ? "Cancelar presença" : "Sair da fila",
        cancelLabel: "Continuar inscrito", tone: "danger",
      }, scope);
      if (!confirmed || !guard.isCurrent(scope)) return;
    }
    await runOnce(`inscricao:${eventId}`, async () => {
      const result = await sessionApi(scope, `/eventos/${eventId}/inscricao`, { method: "POST" });
      accountRequestRef.current += 1;
      setEnrollments((current) => {
        if (!guard.isCurrent(scope)) return current;
        const next = { ...current };
        if (result.status === "CANCELADO") delete next[eventId]; else next[eventId] = result.status;
        return next;
      });
      showNotice(result.message, "success", scope);
      await refreshAfterAction(scope);
      return true;
    }, scope);
  }
  async function addComment(eventId, text) {
    if (!text.trim()) return false;
    return runOnce(`comentario:${eventId}`, async (scope) => {
      const saved = await sessionApi(scope, `/eventos/${eventId}/comentarios`, { method: "POST", body: JSON.stringify({ text: text.trim() }) });
      eventRequestRef.current += 1;
      applyEvents(eventsRef.current.map((item) => item.id === eventId
        ? { ...item, comments: [...item.comments, { author: saved.author, text: saved.text, time: saved.time }] } : item));
      return true;
    });
  }
  async function deleteEvent(eventId) {
    const scope = guard.capture();
    const event = eventsRef.current.find((item) => item.id === eventId);
    if (!event || pendingRef.current.has(`exclusao:${eventId}`)) return;
    if (!userRef.current?.isModerator && event.organizer.id !== userRef.current?.id) return;
    const confirmed = await askConfirm({ title: "Excluir este rolé?", message: `"${event.title}" será excluído para todos os inscritos. Essa ação não pode ser desfeita.`, confirmLabel: "Excluir rolé", tone: "danger" }, scope);
    if (!confirmed || !guard.isCurrent(scope)) return;
    await runOnce(`exclusao:${eventId}`, async () => {
      await sessionApi(scope, `/eventos/${eventId}`, { method: "DELETE" });
      eventRequestRef.current += 1;
      applyEvents(eventsRef.current.filter((item) => item.id !== eventId));
      setSelectedEventId(null);
      showNotice("Rolé excluído com sucesso.", "success", scope);
      await refreshAccount(scope, { reportError: true });
    }, scope);
  }
  async function changePresence(eventId, participantId, present) {
    return runOnce(`presenca:${eventId}:${participantId}`, async (scope) => {
      await sessionApi(scope, `/eventos/${eventId}/presencas/${participantId}`, { method: "PUT", body: JSON.stringify({ presente: present }) });
      await loadManagement(eventId, scope);
      await refreshAccount(scope, { reportError: true });
      return true;
    });
  }
  async function concludeEvent(eventId) {
    return runOnce(`conclusao:${eventId}`, async (scope) => {
      const concluded = await sessionApi(scope, `/eventos/${eventId}/concluir`, { method: "POST" });
      eventRequestRef.current += 1;
      applyEvents(eventsRef.current.map((item) => item.id === eventId ? concluded : item));
      showNotice("Rolé concluído. Presenças e ausências foram registradas.", "success", scope);
      await Promise.all([refreshAfterAction(scope), loadManagement(eventId, scope)]);
      return true;
    });
  }
  async function sendRating(eventId, participantId, nota, comentario) {
    return runOnce(`avaliacao:${eventId}:${participantId}`, async (scope) => {
      await sessionApi(scope, `/eventos/${eventId}/avaliacoes`, { method: "POST", body: JSON.stringify({ avaliadoId: participantId, nota, comentario: comentario.trim() || null }) });
      accountRequestRef.current += 1;
      setRatings((current) => guard.isCurrent(scope) ? { ...current, eventos: current.eventos.map((event) => event.eventoId === eventId
        ? { ...event, participantes: event.participantes.filter((person) => person.id !== participantId) } : event).filter((event) => event.participantes.length) } : current);
      showNotice("Avaliação registrada com sucesso.", "success", scope);
      await refreshAfterAction(scope);
      return true;
    });
  }
  function reportEvent(eventId) {
    const event = eventsRef.current.find((item) => item.id === eventId);
    if (!event || !userRef.current) return;
    setReports((current) => [{ id: createId(), target: event.title, author: userRef.current.name, reason: "Denúncia registrada pelo protótipo para análise manual.", status: "PENDENTE" }, ...current]);
    showNotice("Denúncia enviada à moderação para análise.", "success");
  }
  function resolveReport(reportId, shouldLockEvent) {
    setReports((current) => current.map((item) => item.id === reportId ? { ...item, status: shouldLockEvent ? "PROCEDENTE" : "IMPROCEDENTE" } : item));
  }
  if (!currentUser) return <LoginScreen key={sessionVersion} onLogin={handleLogin} onRegister={handleRegister} notice={alert} onCloseNotice={() => setAlert(null)} />;
  return (
    <div className="app-shell">
      <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
      <Sidebar view={view} setView={handleSetView} currentUser={currentUser} onLogout={handleLogout} />
      <div className="main-column">
        <div className="topbar"><div className="topbar-brand"><Brand /></div><ThemeToggle /></div>
        <main id="conteudo" className="content" tabIndex={-1}>
          {view === "feed" && <FeedView events={filteredEvents} totalEvents={events.length} loadState={loadState} loadingSlow={loadingSlow}
            retryLoad={() => { loadEvents(guard.capture(), { initial: true }); refreshAccount(guard.capture(), { reportError: true }); }} metrics={metrics}
            filters={filters} updateFilter={(name, value) => setFilters((current) => ({ ...current, [name]: value }))}
            enrollments={enrollments} enrollmentsReady={enrollmentsReady} pending={pending} currentUser={currentUser}
            resetFilters={() => setFilters(initialFilters)} openEvent={setSelectedEventId} toggleEnrollment={toggleEnrollment} deleteEvent={deleteEvent} onCreate={() => handleSetView("create")} />}
          {view === "create" && <CreateView key={currentUser.capacityLimit} categoryOptions={categories} capacityLimit={currentUser.capacityLimit} onSubmit={createEvent} submitting={Boolean(pending.criacao)} />}
          {view === "profile" && <ProfileView key={sessionVersion} events={events} enrollments={enrollments} currentUser={currentUser} openEvent={setSelectedEventId} onLogout={handleLogout}
            ratings={ratings} ratingsEventId={ratingsEventId} pending={pending} sendRating={sendRating} accountLoading={accountLoading} accountError={accountError}
            refreshAccount={() => refreshAccount(guard.capture(), { reportError: true })} />}
          {view === "moderation" && currentUser.isModerator && <ModerationView reports={reports} resolveReport={resolveReport} />}
        </main>
      </div>
      {alert && !selectedEvent && <div className="toast-region"><Notice key={alert.id} alert={alert} onClose={() => setAlert(null)} /></div>}
      {selectedEvent && <EventModal key={`${sessionVersion}:${selectedEvent.id}`} event={selectedEvent} enrollment={enrollments[selectedEvent.id]} enrollmentsReady={enrollmentsReady}
        pending={pending} close={() => setSelectedEventId(null)} notice={alert} onCloseNotice={() => setAlert(null)} toggleEnrollment={toggleEnrollment}
        addComment={addComment} deleteEvent={deleteEvent} reportEvent={reportEvent} currentUser={currentUser} inactive={Boolean(confirmState)}
        management={management[selectedEvent.id]} reloadManagement={() => loadManagement(selectedEvent.id)} changePresence={changePresence} concludeEvent={concludeEvent}
        hasPendingRatings={ratings.eventos.some((item) => item.eventoId === selectedEvent.id)} openRatings={openRatings} />}
      {confirmState && <ConfirmDialog title={confirmState.title} message={confirmState.message} confirmLabel={confirmState.confirmLabel}
        cancelLabel={confirmState.cancelLabel} tone={confirmState.tone} onConfirm={() => settleConfirm(true)} onCancel={() => settleConfirm(false)} />}
    </div>
  );
}
