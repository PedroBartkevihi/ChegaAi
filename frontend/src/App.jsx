"use client";

import { useEffect, useMemo, useRef, useState, useCallback } from "react";
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
import { categories, initialFilters, isAmbassador, createId, sortByDate } from "./lib/eventos";
// A API não diz se a pessoa está inscrita: a lista traz só os primeiros nomes de confirmados e da fila.
function enrollmentFor(event, user) {
  if (event.confirmed.includes(user.name)) return "CONFIRMADO";
  if (event.waitlist.includes(user.name)) return "FILA";
  return null;
}
import { useDemora } from "./lib/useDemora";
export default function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const [events, setEvents] = useState([]);
  const [enrollments, setEnrollments] = useState({});
  const [reports, setReports] = useState([]);
  const [view, setView] = useState("feed");
  const [filters, setFilters] = useState(initialFilters);
  const [selectedEventId, setSelectedEventId] = useState(null);
  const [alert, setAlert] = useState(null);
  // "loading" só na primeira carga (a lista ainda vazia); depois de uma ação, a lista atualiza
  // em silêncio e quem mostra o andamento é o botão da ação.
  const [loadState, setLoadState] = useState("idle");
  const loadingSlow = useDemora(loadState === "loading");
  // Ações em andamento ("inscricao:<id>", "exclusao:<id>", "comentario:<id>", "criacao"). O ref barra
  // o segundo clique antes de o React redesenhar o botão desabilitado.
  const [pending, setPending] = useState({});
  const pendingRef = useRef(new Set());
  const [confirmState, setConfirmState] = useState(null);
  const capacityLimit = currentUser && isAmbassador(currentUser.honor) ? 50 : 20;
  const selectedEvent = events.find((event) => event.id === selectedEventId);
  const loadEvents = useCallback(async ({ initial = false } = {}) => {
    if (initial) setLoadState("loading");
    try {
      const data = await api("/eventos");
      setEvents(data);
      if (currentUser) {
        const newEnrollments = {};
        data.forEach((event) => {
          const status = enrollmentFor(event, currentUser);
          if (status) newEnrollments[event.id] = status;
        });
        setEnrollments(newEnrollments);
      }
      setLoadState("ready");
      return true;
    } catch (err) {
      console.error("Erro ao carregar eventos:", err);
      if (initial) {
        setLoadState("error");
        showNotice("Não foi possível carregar os eventos. Verifique sua conexão ou se o servidor está ativo.", "error");
      }
      return false;
    }
  }, [currentUser]);
  useEffect(() => {
    if (currentUser) {
      loadEvents({ initial: true });
    }
  }, [currentUser, loadEvents]);
  useEffect(() => {
    // Na hospedagem gratuita a API dorme quando fica parada: acordá-la já na abertura da
    // página adianta a espera enquanto a pessoa ainda está na tela de login.
    api("/actuator/health").catch(() => {});
    const savedUser = localStorage.getItem(USER_KEY);
    if (!savedUser || !localStorage.getItem(TOKEN_KEY)) {
      clearSession();
      return;
    }
    try {
      setCurrentUser(JSON.parse(savedUser));
    } catch {
      clearSession();
      return;
    }
    api("/auth/me")
      .then((user) => {
        setCurrentUser(user);
        localStorage.setItem(USER_KEY, JSON.stringify(user));
      })
      .catch(() => {});
  }, []);
  useEffect(() => {
    function handleSessionExpired() {
      handleLogout();
      showNotice("Sua sessão expirou. Entre novamente.", "error");
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
  }, []);
  const filteredEvents = useMemo(() => {
    return sortByDate(events.filter((event) => {
      const haystack = `${event.title} ${event.category} ${event.place} ${event.description} ${event.organizer.name}`.toLowerCase();
      const matchesCategory = filters.category === "todos" || event.category === filters.category;
      const matchesHonor = event.organizer.honor >= Number(filters.honor);
      return matchesCategory && matchesHonor && haystack.includes(filters.search.toLowerCase());
    }));
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
  async function runOnce(key, action) {
    if (pendingRef.current.has(key)) return;
    pendingRef.current.add(key);
    setPending((current) => ({ ...current, [key]: true }));
    try {
      return await action();
    } finally {
      pendingRef.current.delete(key);
      setPending((current) => {
        const next = { ...current };
        delete next[key];
        return next;
      });
    }
  }
  function askConfirm(options) {
    return new Promise((resolve) => setConfirmState({ ...options, resolve }));
  }
  function settleConfirm(answer) {
    confirmState?.resolve(answer);
    setConfirmState(null);
  }
  async function handleLogin(email, password) {
    const session = await api("/auth/login", {
      semToken: true,
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    startSession(session, `Bem-vindo(a), ${session.user.name}!`);
  }
  async function handleRegister(account) {
    const session = await api("/auth/cadastro", {
      semToken: true,
      method: "POST",
      body: JSON.stringify(account),
    });
    startSession(session, `Conta criada! Bem-vindo(a), ${session.user.name}!`);
  }
  function startSession({ token, user }, welcomeMessage) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    setCurrentUser(user);
    showNotice(welcomeMessage, "success");
  }
  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }
  function handleLogout() {
    setCurrentUser(null);
    setEvents([]);
    setEnrollments({});
    setLoadState("idle");
    clearSession();
    setView("feed");
  }
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
    await runOnce("criacao", async () => {
      let criado;
      try {
        criado = await api("/eventos", {
          method: "POST",
          body: JSON.stringify(dto),
        });
      } catch (err) {
        showNotice("Não foi possível criar o evento: " + err.message, "error");
        return;
      }
      // O rolé já existe: entra na lista com o que a API devolveu, mesmo que a atualização falhe,
      // para ninguém achar que a criação falhou e criar de novo.
      setEvents((current) => [...current.filter((event) => event.id !== criado.id), criado]);
      setEnrollments((current) => setEnrollment(current, criado.id, enrollmentFor(criado, currentUser)));
      formElement.reset();
      setView("feed");
      setSelectedEventId(criado.id);
      showNotice("Rolé criado com sucesso!", "success");
      if (!(await loadEvents())) {
        showNotice("Rolé criado, mas não foi possível atualizar a lista agora. Recarregue a página para ver as vagas atualizadas.", "info");
      }
    });
  }
  async function toggleEnrollment(eventId) {
    // Um segundo clique durante o envio não pode abrir outra confirmação e desfazer o primeiro.
    if (pendingRef.current.has(`inscricao:${eventId}`)) return;
    const event = events.find((item) => item.id === eventId);
    const enrollment = enrollments[eventId];
    if (event && (enrollment === "CONFIRMADO" || enrollment === "FILA")) {
      const leavingSeat = enrollment === "CONFIRMADO";
      const confirmed = await askConfirm({
        title: leavingSeat ? "Cancelar sua presença?" : "Sair da lista de espera?",
        message: leavingSeat
          ? `Sua vaga em "${event.title}" pode ir na hora para a próxima pessoa da fila, e não há garantia de recuperá-la.`
          : `Você perde o seu lugar na fila de "${event.title}".`,
        confirmLabel: leavingSeat ? "Cancelar presença" : "Sair da fila",
        cancelLabel: "Continuar inscrito",
        tone: "danger",
      });
      if (!confirmed) return;
    }
    await runOnce(`inscricao:${eventId}`, async () => {
      let result;
      try {
        result = await api(`/eventos/${eventId}/inscricao`, {
          method: "POST",
        });
      } catch (err) {
        showNotice("Não foi possível realizar a inscrição: " + err.message, "error");
        return;
      }
      // A situação devolvida vale já: se a atualização da lista falhar, o botão não pode continuar
      // oferecendo a ação antiga (um novo clique desfaria a inscrição sem confirmação).
      setEnrollments((current) => setEnrollment(current, eventId, result.status === "CANCELADO" ? null : result.status));
      showNotice(result.message, "success");
      if (!(await loadEvents())) {
        showNotice(`${result.message} Não foi possível atualizar a lista agora; recarregue a página para ver as vagas atualizadas.`, "info");
      }
    });
  }
  function setEnrollment(current, eventId, status) {
    const next = { ...current };
    if (status) next[eventId] = status;
    else delete next[eventId];
    return next;
  }
  async function addComment(eventId, text) {
    if (!text.trim()) return false;
    const saved = await runOnce(`comentario:${eventId}`, async () => {
      try {
        const novoComentario = await api(`/eventos/${eventId}/comentarios`, {
          method: "POST",
          body: JSON.stringify({ text: text.trim() }),
        });
        setEvents((current) =>
          current.map((event) =>
            event.id === eventId
              ? {
                  ...event,
                  comments: [
                    ...event.comments,
                    {
                      author: novoComentario.author,
                      text: novoComentario.text,
                      time: novoComentario.time,
                    },
                  ],
                }
              : event
          )
        );
        return true;
      } catch (err) {
        showNotice("Não foi possível publicar o comentário: " + err.message, "error");
        return false;
      }
    });
    return saved === true;
  }
  async function deleteEvent(eventId) {
    if (pendingRef.current.has(`exclusao:${eventId}`)) return;
    const event = events.find((item) => item.id === eventId);
    if (!event) return;
    const canDelete = currentUser.isModerator || event.organizer.id === currentUser.id;
    if (!canDelete) {
      showNotice("Apenas o organizador ou um moderador pode excluir este rolé.", "error");
      return;
    }
    const confirmed = await askConfirm({
      title: "Excluir este rolé?",
      message: `"${event.title}" será excluído para todos os inscritos. Essa ação não pode ser desfeita.`,
      confirmLabel: "Excluir rolé",
      tone: "danger",
    });
    if (!confirmed) return;
    await runOnce(`exclusao:${eventId}`, async () => {
      try {
        await api(`/eventos/${eventId}`, { method: "DELETE" });
      } catch (err) {
        showNotice("Não foi possível excluir o evento: " + err.message, "error");
        return;
      }
      setEvents((current) => current.filter((item) => item.id !== eventId));
      setEnrollments((current) => setEnrollment(current, eventId, null));
      setSelectedEventId(null);
      showNotice("Rolé excluído com sucesso.", "success");
    });
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
    setAlert({ message: cleanMsg, type, id: createId() });
  }
  // Só o aviso de sucesso some sozinho; erro e aviso informativo ficam até a pessoa fechar.
  useEffect(() => {
    if (alert?.type === "success") {
      const timer = setTimeout(() => setAlert(null), 6000);
      return () => clearTimeout(timer);
    }
  }, [alert]);
  function handleSetView(newView) {
    if (newView === "create" && currentUser && currentUser.honor < 2.0) {
      showNotice("Ação Não Permitida. Sua reputação/honra atual é inferior a 2.0. Aumente sua honra para criar rolés.", "error");
      return;
    }
    setView(newView);
  }

  useEffect(() => {
    if (currentUser && !currentUser.isModerator && view === "moderation") {
      setView("feed");
    }
  }, [currentUser, view]);
  if (!currentUser) {
    return <LoginScreen onLogin={handleLogin} onRegister={handleRegister} notice={alert} onCloseNotice={() => setAlert(null)} />;
  }
  return (
    <div className="app-shell">
      <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
      <Sidebar
        view={view}
        setView={handleSetView}
        currentUser={currentUser}
        onLogout={handleLogout}
      />
      <div className="main-column">
        {/* Chave de tema no canto superior direito; no celular, o topo também mostra a marca. */}
        <div className="topbar">
          <div className="topbar-brand"><Brand /></div>
          <ThemeToggle />
        </div>
        <main id="conteudo" className="content" tabIndex={-1}>
          {view === "feed" && (
            <FeedView
              events={filteredEvents}
              totalEvents={events.length}
              loadState={loadState}
              loadingSlow={loadingSlow}
              retryLoad={() => loadEvents({ initial: true })}
              metrics={metrics}
              filters={filters}
              updateFilter={updateFilter}
              enrollments={enrollments}
              pending={pending}
              currentUser={currentUser}
              resetFilters={() => setFilters(initialFilters)}
              openEvent={setSelectedEventId}
              toggleEnrollment={toggleEnrollment}
              deleteEvent={deleteEvent}
              onCreate={() => handleSetView("create")}
            />
          )}
          {view === "create" && <CreateView categoryOptions={categories} capacityLimit={capacityLimit} onSubmit={createEvent} submitting={Boolean(pending.criacao)} />}
          {view === "profile" && <ProfileView events={events} enrollments={enrollments} currentUser={currentUser} openEvent={setSelectedEventId} onLogout={handleLogout} />}
          {view === "moderation" && currentUser.isModerator && <ModerationView reports={reports} resolveReport={resolveReport} />}
        </main>
      </div>
      {/* Avisos flutuam na tela para aparecerem onde a pessoa estiver; com o detalhe de um rolé
          aberto, aparecem dentro dele, onde está o foco. */}
      {alert && !selectedEvent && (
        <div className="toast-region">
          <Notice key={alert.id} alert={alert} onClose={() => setAlert(null)} />
        </div>
      )}
      {selectedEvent && (
        <EventModal
          event={selectedEvent}
          enrollment={enrollments[selectedEvent.id]}
          pending={pending}
          close={() => setSelectedEventId(null)}
          notice={alert}
          onCloseNotice={() => setAlert(null)}
          toggleEnrollment={toggleEnrollment}
          addComment={addComment}
          deleteEvent={deleteEvent}
          reportEvent={reportEvent}
          currentUser={currentUser}
        />
      )}
      {confirmState && (
        <ConfirmDialog
          title={confirmState.title}
          message={confirmState.message}
          confirmLabel={confirmState.confirmLabel}
          cancelLabel={confirmState.cancelLabel}
          tone={confirmState.tone}
          onConfirm={() => settleConfirm(true)}
          onCancel={() => settleConfirm(false)}
        />
      )}
    </div>
  );
}
