"use client";

import { useEffect, useMemo, useState, useCallback } from "react";
import Notice from "./components/Notice";
import LoginScreen from "./components/LoginScreen";
import SiteHeader from "./components/SiteHeader";
import FeedView from "./components/FeedView";
import CreateView from "./components/CreateView";
import ProfileView from "./components/ProfileView";
import ModerationView from "./components/ModerationView";
import EventModal from "./components/EventModal";
import { api, cleanErrorMessage, TOKEN_KEY, USER_KEY, SESSION_EXPIRED_EVENT } from "./lib/api";
import { categories, initialFilters, isAmbassador, createId } from "./lib/eventos";
import { useDemora, AVISO_SERVIDOR_ACORDANDO } from "./lib/useDemora";
export default function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const [events, setEvents] = useState([]);
  const [enrollments, setEnrollments] = useState({});
  const [reports, setReports] = useState([]);
  const [view, setView] = useState("feed");
  const [filters, setFilters] = useState(initialFilters);
  const [selectedEventId, setSelectedEventId] = useState(null);
  const [alert, setAlert] = useState(null); 
  const [loading, setLoading] = useState(false);
  const loadingSlow = useDemora(loading);
  const capacityLimit = currentUser && isAmbassador(currentUser.honor) ? 50 : 20;
  const selectedEvent = events.find((event) => event.id === selectedEventId);
  const loadEvents = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api("/eventos");
      setEvents(data);
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
  useEffect(() => {
    if (currentUser) {
      loadEvents();
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
  async function handleLogin(email, password) {
    const session = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    startSession(session, `Bem-vindo(a), ${session.user.name}!`);
  }
  async function handleRegister(account) {
    const session = await api("/auth/cadastro", {
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
    try {
      setLoading(true);
      const criado = await api("/eventos", {
        method: "POST",
        body: JSON.stringify(dto),
      });
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
  async function toggleEnrollment(eventId) {
    try {
      setLoading(true);
      const result = await api(`/eventos/${eventId}/inscricao`, {
        method: "POST",
      });
      showNotice(result.message, "success");
      await loadEvents();
    } catch (err) {
      showNotice("Não foi possível realizar a inscrição: " + err.message, "error");
    } finally {
      setLoading(false);
    }
  }
  async function addComment(eventId, text) {
    if (!text.trim()) return;
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
      <SiteHeader
        view={view}
        setView={handleSetView}
        currentUser={currentUser}
        onLogout={handleLogout}
      />
      <main className="content">
        {alert && <Notice alert={alert} onClose={() => setAlert(null)} />}
        {loading && <div className="notice loading-notice">{loadingSlow ? AVISO_SERVIDOR_ACORDANDO : "Carregando..."}</div>}
        {view === "feed" && (
          <FeedView
            events={filteredEvents}
            metrics={metrics}
            filters={filters}
            updateFilter={updateFilter}
            enrollments={enrollments}
            currentUser={currentUser}
            resetFilters={() => setFilters(initialFilters)}
            openEvent={setSelectedEventId}
            toggleEnrollment={toggleEnrollment}
            deleteEvent={deleteEvent}
            onCreate={() => handleSetView("create")}
          />
        )}
        {view === "create" && <CreateView categoryOptions={categories} capacityLimit={capacityLimit} onSubmit={createEvent} />}
        {view === "profile" && <ProfileView events={events} enrollments={enrollments} currentUser={currentUser} />}
        {view === "moderation" && currentUser.isModerator && <ModerationView reports={reports} resolveReport={resolveReport} />}
      </main>
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
    </div>
  );
}
