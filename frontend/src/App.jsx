"use client";

import { useEffect, useRef, useState } from "react";
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
import { categories, initialFilters, createId } from "./lib/eventos";
import { EMPTY_SECTION, PAGE_SIZE, SEARCH_DELAY, appendPage, feedPath, refreshPages, toSummary, withCounts } from "./lib/feed";
import { useDemora } from "./lib/useDemora";

const EMPTY_RATINGS = { podeAvaliar: false, eventos: [] };
const EMPTY_FEED = { proximos: EMPTY_SECTION, anteriores: EMPTY_SECTION };
const SECTIONS = ["proximos", "anteriores"];
// Feed, resumo e detalhe são públicos: sem o token, cada página ou busca nova vai sem preflight de CORS.
const PUBLIC_READ = { semToken: true };
const CREATION_BLOCKED = "Sua conta não pode criar rolés no momento. Confira sua reputação ou aguarde o fim da suspensão.";

export default function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const userRef = useRef(null);
  const [sessionVersion, setSessionVersion] = useState(0);
  const guardRef = useRef(null);
  if (!guardRef.current) guardRef.current = createSessionGuard(() => localStorage.getItem(TOKEN_KEY));
  const guard = guardRef.current;
  const [feed, setFeed] = useState(EMPTY_FEED);
  const feedRef = useRef(EMPTY_FEED);
  const [summary, setSummary] = useState(null);
  const [showPast, setShowPast] = useState(false);
  const showPastRef = useRef(false);
  const [detail, setDetail] = useState(null);
  const detailRef = useRef(null);
  const [registrations, setRegistrations] = useState([]);
  const registrationsRef = useRef([]);
  const [enrollments, setEnrollments] = useState({});
  const [enrollmentsReady, setEnrollmentsReady] = useState(false);
  const [ratings, setRatings] = useState(EMPTY_RATINGS);
  const [accountLoading, setAccountLoading] = useState(false);
  const [accountError, setAccountError] = useState(false);
  const [management, setManagement] = useState({});
  const [reports, setReports] = useState([]);
  const [view, setView] = useState("feed");
  const [filters, setFilters] = useState(initialFilters);
  const [appliedSearch, setAppliedSearch] = useState("");
  const appliedRef = useRef(initialFilters);
  appliedRef.current = { ...filters, search: appliedSearch };
  const [selectedEventId, setSelectedEventId] = useState(null);
  const selectedRef = useRef(null);
  const [ratingsEventId, setRatingsEventId] = useState(null);
  const [alert, setAlert] = useState(null);
  const loadingSlow = useDemora(feed.proximos.state === "loading" && !feed.proximos.items.length);
  const [pending, setPending] = useState({});
  const pendingRef = useRef(new Map());
  const [confirmState, setConfirmState] = useState(null);
  const confirmRef = useRef(null);
  const sectionRequestRef = useRef({ proximos: 0, anteriores: 0 });
  const summaryRequestRef = useRef(0);
  const detailRequestRef = useRef(0);
  // Versão de cada rolé mudado por uma ação (null se excluído): uma página pedida antes dela não a desfaz.
  const changesRef = useRef(new Map());
  const changeSeqRef = useRef(0);
  const accountRequestRef = useRef(0);
  const managementRequestsRef = useRef(new Map());
  const loginRequestRef = useRef(0);
  const selectedEvent = findEvent(selectedEventId);

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
  function updateFeed(update) {
    feedRef.current = update(feedRef.current);
    setFeed(feedRef.current);
  }
  function updateSection(section, patch) {
    updateFeed((current) => ({ ...current, [section]: { ...current[section], ...(typeof patch === "function" ? patch(current[section]) : patch) } }));
  }
  function updateDetail(update) {
    detailRef.current = typeof update === "function" ? update(detailRef.current) : update;
    setDetail(detailRef.current);
  }
  function applyRegistrations(list) {
    registrationsRef.current = list;
    setRegistrations(list);
  }
  // O rolé pode estar no detalhe aberto, numa das seções do feed ou só nas inscrições do perfil.
  function findEvent(eventId) {
    if (!eventId) return null;
    const opened = detailRef.current;
    if (opened?.id === eventId && opened.data) return withCounts(opened.data);
    const { proximos, anteriores } = feedRef.current;
    return proximos.items.find((item) => item.id === eventId) || anteriores.items.find((item) => item.id === eventId)
      || registrationsRef.current.find((item) => item.eventoId === eventId)?.evento || null;
  }
  function rememberChange(eventId, event) {
    changeSeqRef.current += 1;
    changesRef.current.set(eventId, { seq: changeSeqRef.current, event });
  }
  function applyEvent(event) {
    rememberChange(event.id, event);
    updateFeed((current) => Object.fromEntries(SECTIONS.map((name) => [name,
      { ...current[name], items: current[name].items.map((item) => (item.id === event.id ? event : item)) }])));
  }
  function removeEvent(eventId) {
    const sections = SECTIONS.filter((name) => feedRef.current[name].items.some((item) => item.id === eventId));
    rememberChange(eventId, null);
    updateFeed((current) => Object.fromEntries(SECTIONS.map((name) => [name, sections.includes(name)
      ? { ...current[name], items: current[name].items.filter((item) => item.id !== eventId), total: Math.max(current[name].total - 1, 0) }
      : current[name]])));
    return sections;
  }
  function resetSection(section) {
    sectionRequestRef.current[section] += 1;
    updateSection(section, EMPTY_SECTION);
  }
  function selectEvent(eventId, data = null) {
    selectedRef.current = eventId;
    detailRequestRef.current += 1;
    updateDetail(eventId && data ? { id: eventId, state: "ready", data } : null);
    setSelectedEventId(eventId);
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
    updateFeed(() => EMPTY_FEED);
    changesRef.current = new Map();
    setSummary(null);
    showPastRef.current = false;
    setShowPast(false);
    applyRegistrations([]);
    setEnrollments({});
    setEnrollmentsReady(false);
    setRatings(EMPTY_RATINGS);
    setManagement({});
    setReports([]);
    selectEvent(null);
    setRatingsEventId(null);
    setAccountLoading(false);
    setAccountError(false);
    setAlert(null);
    setView("feed");
    setFilters(initialFilters);
    setAppliedSearch("");
    return scope;
  }
  function handleLogout() {
    loginRequestRef.current += 1;
    replaceSession(null, null);
  }
  // mode: "replace" (primeira página, ao entrar ou ao mudar os filtros), "refresh" (as páginas já abertas, de novo)
  // ou "more" (a página seguinte, acrescentada à lista).
  async function loadSection(section, scope = guard.capture(), mode = "replace") {
    if (!scope.token || !guard.isCurrent(scope)) return false;
    const current = feedRef.current[section];
    const more = mode === "more";
    // Com filtros novos a caminho, a página seguinte seria a da busca nova emendada na lista antiga.
    if (more && (!current.hasNext || current.more === "loading" || current.state === "loading")) return false;
    const request = more ? sectionRequestRef.current[section] : ++sectionRequestRef.current[section];
    const since = changeSeqRef.current;
    const pages = mode === "refresh" ? refreshPages(current) : 1;
    const path = more ? feedPath(section, appliedRef.current, current.pages) : feedPath(section, appliedRef.current, 0, pages * PAGE_SIZE);
    // A atualização das páginas abertas é silenciosa: a lista só muda quando a resposta chega.
    updateSection(section, more ? { more: "loading" } : mode === "replace" ? { state: "loading", more: "idle" } : { more: "idle" });
    try {
      const page = await sessionApi(scope, path, PUBLIC_READ);
      if (request !== sectionRequestRef.current[section]) return false;
      const items = page.items.flatMap((item) => {
        const change = changesRef.current.get(item.id);
        return change?.seq > since ? (change.event ? [change.event] : []) : [item];
      });
      updateSection(section, (latest) => (more
        ? { items: appendPage(latest.items, items), pages: latest.pages + 1, total: page.total, hasNext: page.hasNext, more: "idle" }
        : { items, pages, total: page.total, hasNext: page.hasNext, state: "ready" }));
      return true;
    } catch (error) {
      if (!guard.isCurrent(scope) || isDiscardedRequest(error) || request !== sectionRequestRef.current[section]) return false;
      if (more) {
        updateSection(section, { more: "idle" });
        showNotice("Não foi possível carregar mais rolés. Tente de novo.", "error", scope);
      } else if (mode === "replace" || !feedRef.current[section].items.length) {
        // Uma atualização que substituiu o carregamento inicial e falhou também encerra a espera.
        updateSection(section, { ...EMPTY_SECTION, state: "error" });
        showNotice(section === "proximos"
          ? "Não foi possível carregar os rolés. Verifique sua conexão e tente de novo."
          : "Não foi possível carregar os rolés anteriores. Tente de novo.", "error", scope);
      } else updateSection(section, { state: "ready" });
      return false;
    }
  }
  async function loadSummary(scope = guard.capture()) {
    if (!scope.token || !guard.isCurrent(scope)) return false;
    const request = ++summaryRequestRef.current;
    try {
      const data = await sessionApi(scope, "/eventos/resumo", PUBLIC_READ);
      if (request !== summaryRequestRef.current) return false;
      setSummary(data);
      return true;
    } catch {
      // Sem resposta, os números do topo ficam como estavam.
      return false;
    }
  }
  async function loadDetail(eventId, scope = guard.capture()) {
    if (!guard.isCurrent(scope)) return false;
    const request = ++detailRequestRef.current;
    updateDetail((current) => (current?.id === eventId ? { ...current, state: current.data ? "ready" : "loading" } : { id: eventId, state: "loading", data: null }));
    try {
      const data = await sessionApi(scope, `/eventos/${eventId}`, PUBLIC_READ);
      if (request !== detailRequestRef.current) return false;
      updateDetail({ id: eventId, state: "ready", data });
      applyEvent(toSummary(data));
      return true;
    } catch (error) {
      if (!guard.isCurrent(scope) || isDiscardedRequest(error) || request !== detailRequestRef.current) return false;
      if (error.status === 404) {
        forgetEvent(eventId, scope);
        showNotice("Este rolé não está mais disponível.", "error", scope);
        return false;
      }
      updateDetail((current) => (current?.id === eventId ? { ...current, state: current.data ? "ready" : "error" } : current));
      if (!findEvent(eventId)) {
        selectEvent(null);
        showNotice("Não foi possível abrir o rolé. Tente de novo.", "error", scope);
      }
      return false;
    }
  }
  function forgetEvent(eventId, scope) {
    removeEvent(eventId);
    if (selectedRef.current === eventId) selectEvent(null);
    loadSummary(scope);
  }
  // Depois de uma ação, só o rolé afetado volta da API (com o detalhe, se estiver aberto).
  async function reloadEvent(eventId, scope) {
    if (selectedRef.current === eventId) return loadDetail(eventId, scope);
    try {
      applyEvent(toSummary(await sessionApi(scope, `/eventos/${eventId}`, PUBLIC_READ)));
      return true;
    } catch (error) {
      if (guard.isCurrent(scope) && !isDiscardedRequest(error) && error.status === 404) forgetEvent(eventId, scope);
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
    const [profile, enrolled, pendingRatings] = results;
    if (profile.status === "fulfilled") {
      userRef.current = profile.value;
      setCurrentUser(profile.value);
      localStorage.setItem(USER_KEY, JSON.stringify(profile.value));
    }
    if (enrolled.status === "fulfilled") {
      applyRegistrations(enrolled.value);
      setEnrollments(Object.fromEntries(enrolled.value.map((item) => [item.eventoId, item.status])));
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
    const event = findEvent(eventId);
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
  async function refreshAfterAction(scope, eventId) {
    const [event] = await Promise.all([reloadEvent(eventId, scope), loadSummary(scope), refreshAccount(scope, { reportError: true })]);
    return event;
  }
  // Ao voltar para a aba: as páginas abertas do feed, os números do topo, a conta e o rolé aberto.
  function refreshFeed(scope) {
    loadSection("proximos", scope, "refresh");
    if (showPastRef.current) loadSection("anteriores", scope, "refresh");
    else resetSection("anteriores");
    loadSummary(scope);
    refreshAccount(scope);
    if (selectedRef.current) loadDetail(selectedRef.current, scope);
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
  // Na renderização em que a sessão é trocada o estado ainda é o anterior: quem carrega é a renderização seguinte.
  useEffect(() => {
    const scope = guard.capture();
    if (!scope.token || scope.generation !== sessionVersion) return;
    loadSummary(scope);
    refreshAccount(scope, { reportError: true });
    const refresh = () => {
      if (document.visibilityState === "visible" && guard.isCurrent(scope)) refreshFeed(scope);
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
  // Filtros novos trazem a primeira página; os anteriores só são pedidos com a seção aberta.
  const filterKey = [filters.category, filters.honor, appliedSearch.trim()].join("|");
  useEffect(() => {
    const scope = guard.capture();
    if (!scope.token || scope.generation !== sessionVersion) return;
    loadSection("proximos", scope);
    if (showPastRef.current) loadSection("anteriores", scope);
    else resetSection("anteriores");
  }, [sessionVersion, filterKey]);
  useEffect(() => {
    if (filters.search === appliedSearch) return;
    const timer = setTimeout(() => setAppliedSearch(filters.search), SEARCH_DELAY);
    return () => clearTimeout(timer);
  }, [filters.search]);
  useEffect(() => {
    if (!selectedEventId) return;
    loadDetail(selectedEventId);
    loadManagement(selectedEventId);
  }, [selectedEventId, sessionVersion]);
  useEffect(() => {
    if (alert?.type !== "success") return;
    const timer = setTimeout(() => setAlert((current) => current?.id === alert.id ? null : current), 6000);
    return () => clearTimeout(timer);
  }, [alert]);
  function resetFilters() {
    setFilters(initialFilters);
    setAppliedSearch("");
  }
  function togglePast() {
    const open = !showPastRef.current;
    showPastRef.current = open;
    setShowPast(open);
    if (open && ["idle", "error"].includes(feedRef.current.anteriores.state)) loadSection("anteriores");
  }
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
    selectEvent(null);
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
      setEnrollments((current) => guard.isCurrent(scope) ? { ...current, [created.id]: "CONFIRMADO" } : current);
      formElement.reset();
      setView("feed");
      selectEvent(created.id, created);
      showNotice("Rolé criado com sucesso!", "success", scope);
      // O rolé novo entra na posição dele: as páginas abertas dos próximos voltam da API.
      const [listed] = await Promise.all([loadSection("proximos", scope, "refresh"), loadSummary(scope), refreshAccount(scope, { reportError: true })]);
      if (!listed) showNotice("Rolé criado, mas a lista não pôde ser atualizada. Tente novamente.", "info", scope);
      return true;
    });
  }
  async function toggleEnrollment(eventId) {
    const scope = guard.capture();
    if (!enrollmentsReady || pendingRef.current.has(`inscricao:${eventId}`)) return;
    const event = findEvent(eventId);
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
      await refreshAfterAction(scope, eventId);
      return true;
    }, scope);
  }
  async function addComment(eventId, text) {
    if (!text.trim()) return false;
    return runOnce(`comentario:${eventId}`, async (scope) => {
      const saved = await sessionApi(scope, `/eventos/${eventId}/comentarios`, { method: "POST", body: JSON.stringify({ text: text.trim() }) });
      // Uma recarga do detalhe pedida antes do envio não apaga o comentário novo.
      detailRequestRef.current += 1;
      updateDetail((current) => (current?.id === eventId && current.data
        ? { ...current, state: "ready", data: { ...current.data, comments: [...current.data.comments, { author: saved.author, text: saved.text, time: saved.time }] } }
        : current));
      return true;
    });
  }
  async function deleteEvent(eventId) {
    const scope = guard.capture();
    const event = findEvent(eventId);
    if (!event || pendingRef.current.has(`exclusao:${eventId}`)) return;
    if (!userRef.current?.isModerator && event.organizer.id !== userRef.current?.id) return;
    const confirmed = await askConfirm({ title: "Excluir este rolé?", message: `"${event.title}" será excluído para todos os inscritos. Essa ação não pode ser desfeita.`, confirmLabel: "Excluir rolé", tone: "danger" }, scope);
    if (!confirmed || !guard.isCurrent(scope)) return;
    await runOnce(`exclusao:${eventId}`, async () => {
      await sessionApi(scope, `/eventos/${eventId}`, { method: "DELETE" });
      const sections = removeEvent(eventId);
      if (selectedRef.current === eventId) selectEvent(null);
      showNotice("Rolé excluído com sucesso.", "success", scope);
      // Com páginas ainda por carregar, as abertas voltam da API para a próxima não pular um rolé.
      const reloads = sections.filter((name) => feedRef.current[name].hasNext).map((name) => loadSection(name, scope, "refresh"));
      await Promise.all([...reloads, loadSummary(scope), refreshAccount(scope, { reportError: true })]);
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
      if (selectedRef.current === eventId) {
        detailRequestRef.current += 1;
        updateDetail({ id: eventId, state: "ready", data: concluded });
      }
      applyEvent(toSummary(concluded));
      showNotice("Rolé concluído. Presenças e ausências foram registradas.", "success", scope);
      await Promise.all([loadSummary(scope), refreshAccount(scope, { reportError: true }), loadManagement(eventId, scope)]);
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
      await refreshAfterAction(scope, eventId);
      return true;
    });
  }
  function reportEvent(eventId) {
    const event = findEvent(eventId);
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
          {view === "feed" && <FeedView feed={feed} summary={summary} loadingSlow={loadingSlow} showPast={showPast} togglePast={togglePast}
            loadMore={(section) => loadSection(section, guard.capture(), "more")} retryPast={() => loadSection("anteriores")}
            retryLoad={() => { const scope = guard.capture(); loadSection("proximos", scope); loadSummary(scope); refreshAccount(scope, { reportError: true }); }}
            filters={filters} updateFilter={(name, value) => setFilters((current) => ({ ...current, [name]: value }))}
            enrollments={enrollments} enrollmentsReady={enrollmentsReady} pending={pending} currentUser={currentUser}
            resetFilters={resetFilters} openEvent={selectEvent} toggleEnrollment={toggleEnrollment} deleteEvent={deleteEvent} onCreate={() => handleSetView("create")} />}
          {view === "create" && <CreateView key={currentUser.capacityLimit} categoryOptions={categories} capacityLimit={currentUser.capacityLimit} onSubmit={createEvent} submitting={Boolean(pending.criacao)} />}
          {view === "profile" && <ProfileView key={sessionVersion} registrations={registrations} enrollments={enrollments} currentUser={currentUser} openEvent={selectEvent} onLogout={handleLogout}
            ratings={ratings} ratingsEventId={ratingsEventId} pending={pending} sendRating={sendRating} accountLoading={accountLoading} accountError={accountError}
            refreshAccount={() => refreshAccount(guard.capture(), { reportError: true })} />}
          {view === "moderation" && currentUser.isModerator && <ModerationView reports={reports} resolveReport={resolveReport} />}
        </main>
      </div>
      {alert && !selectedEvent && <div className="toast-region"><Notice key={alert.id} alert={alert} onClose={() => setAlert(null)} /></div>}
      {selectedEvent && <EventModal key={`${sessionVersion}:${selectedEvent.id}`} event={selectedEvent} detailState={detail?.id === selectedEvent.id ? detail.state : "loading"}
        reloadDetail={() => loadDetail(selectedEvent.id)} enrollment={enrollments[selectedEvent.id]} enrollmentsReady={enrollmentsReady}
        pending={pending} close={() => selectEvent(null)} notice={alert} onCloseNotice={() => setAlert(null)} toggleEnrollment={toggleEnrollment}
        addComment={addComment} deleteEvent={deleteEvent} reportEvent={reportEvent} currentUser={currentUser} inactive={Boolean(confirmState)}
        management={management[selectedEvent.id]} reloadManagement={() => loadManagement(selectedEvent.id)} changePresence={changePresence} concludeEvent={concludeEvent}
        hasPendingRatings={ratings.eventos.some((item) => item.eventoId === selectedEvent.id)} openRatings={openRatings} />}
      {confirmState && <ConfirmDialog title={confirmState.title} message={confirmState.message} confirmLabel={confirmState.confirmLabel}
        cancelLabel={confirmState.cancelLabel} tone={confirmState.tone} onConfirm={() => settleConfirm(true)} onCancel={() => settleConfirm(false)} />}
    </div>
  );
}
