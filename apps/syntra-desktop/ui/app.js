const DEFAULTS = Object.freeze({
  gatewayBase: "http://127.0.0.1:8080",
  runtimePort: 8090,
  privacyProfile: "private",
  startupBehavior: "manual"
});

function normalizeLoopbackBase(value) {
  try {
    const url = new URL(String(value || "").trim());
    const loopback = url.hostname === "127.0.0.1" || url.hostname === "localhost";
    const cleanPath = url.pathname === "/" || url.pathname === "";
    if (url.protocol !== "http:" || !loopback || !cleanPath || url.username || url.password || url.search || url.hash) return null;
    return `${url.protocol}//${url.host}`;
  } catch (_) {
    return null;
  }
}

function loadRuntimePort() {
  const value = Number.parseInt(localStorage.getItem("syntra.runtimePort") || String(DEFAULTS.runtimePort), 10);
  return Number.isInteger(value) && value > 0 && value <= 65535 ? value : DEFAULTS.runtimePort;
}

const state = {
  control: "NORMAL",
  gatewayBase: normalizeLoopbackBase(localStorage.getItem("syntra.gatewayBase")) || DEFAULTS.gatewayBase,
  runtimePort: loadRuntimePort(),
  privacyProfile: localStorage.getItem("syntra.privacyProfile") || DEFAULTS.privacyProfile,
  startupBehavior: localStorage.getItem("syntra.startupBehavior") || DEFAULTS.startupBehavior,
  prompts: []
};

const controlState = document.getElementById("control-state");
const gatewayStatus = document.getElementById("gateway-status");
const runtimeStatus = document.getElementById("runtime-status");
const timeline = document.getElementById("timeline");
const clock = document.getElementById("clock");
const promptInput = document.getElementById("prompt-input");
const promptStatus = document.getElementById("prompt-status");
const promptCount = document.getElementById("prompt-count");
const chatLog = document.getElementById("chat-log");
const notice = document.getElementById("notice");

function nowLabel() {
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(new Date());
}

function updateClock() { clock.textContent = nowLabel(); }

function showNotice(message, tone = "info") {
  notice.textContent = message;
  notice.dataset.tone = tone;
  notice.classList.add("show");
  window.clearTimeout(showNotice.timer);
  showNotice.timer = window.setTimeout(() => notice.classList.remove("show"), 3200);
}

function addTimelineEvent(title, detail) {
  const row = document.createElement("div");
  row.className = "timeline-event";
  const dot = document.createElement("span");
  dot.className = "event-dot";
  const copy = document.createElement("div");
  const strong = document.createElement("strong");
  const paragraph = document.createElement("p");
  strong.textContent = title;
  paragraph.textContent = detail;
  copy.append(strong, paragraph);
  const time = document.createElement("time");
  time.textContent = nowLabel();
  row.append(dot, copy, time);
  timeline.prepend(row);
  while (timeline.children.length > 8) timeline.removeChild(timeline.lastElementChild);
}

function applyLocalControl(action) {
  const next = { STOP: "STOPPED", PAUSE: "PAUSED", RESUME: "NORMAL", TAKE_CONTROL: "OWNER_CONTROL" }[action];
  if (!next) return;
  state.control = next;
  controlState.textContent = next;
  showNotice(`${action} recorded as local intent only.`, action === "STOP" ? "danger" : "info");
  addTimelineEvent(`Local control intent: ${action}`, "UI state changed locally only. No remote execution success is claimed until an authenticated control endpoint is wired and evidence confirms the result.");
}

async function probeGateway() {
  gatewayStatus.textContent = "Probing…";
  const candidates = [`${state.gatewayBase}/actuator/health`, `${state.gatewayBase}/api/actuator/health`];
  for (const url of candidates) {
    try {
      const response = await fetch(url, { method: "GET", cache: "no-store" });
      if (!response.ok) continue;
      const body = await response.json().catch(() => ({}));
      const status = typeof body.status === "string" ? body.status : "REACHABLE";
      gatewayStatus.textContent = status;
      addTimelineEvent("Local gateway probe returned evidence", `${url} responded with HTTP ${response.status}; reported status: ${status}.`);
      return;
    } catch (_) {}
  }
  gatewayStatus.textContent = "Offline / unavailable";
  addTimelineEvent("Local gateway unavailable", `No supported health response was received from ${state.gatewayBase}. This is expected before the owner-PC stack is running.`);
}

async function probeRuntime() {
  runtimeStatus.textContent = "Probing…";
  const invoke = window.__TAURI__?.core?.invoke;
  if (typeof invoke !== "function") {
    runtimeStatus.textContent = "Native probe unavailable";
    addTimelineEvent("Aetheris runtime probe unavailable", "This page is not running inside the native Syntra shell, so no runtime-health claim is made.");
    return;
  }
  try {
    const result = await invoke("probe_aetheris_runtime", { port: state.runtimePort });
    const reportedStatus = typeof result.reportedStatus === "string" ? result.reportedStatus : "UNKNOWN";
    const httpStatus = Number.isInteger(result.httpStatus) ? result.httpStatus : null;
    if (result.healthy === true) {
      runtimeStatus.textContent = `${reportedStatus} · HTTP ${httpStatus}`;
      addTimelineEvent("Aetheris runtime health verified", result.evidence);
      return;
    }
    if (result.reachable === true) {
      runtimeStatus.textContent = `${reportedStatus} · HTTP ${httpStatus ?? "?"}`;
      addTimelineEvent("Aetheris runtime responded but is not healthy", result.evidence);
      return;
    }
    runtimeStatus.textContent = "Offline / unavailable";
    addTimelineEvent("Aetheris runtime unavailable", result.evidence);
  } catch (error) {
    runtimeStatus.textContent = "Probe failed";
    addTimelineEvent("Aetheris runtime probe failed", `The native probe did not return usable evidence: ${String(error)}`);
  }
}

function renderPrompts() {
  promptCount.textContent = String(state.prompts.length);
  chatLog.replaceChildren();
  if (state.prompts.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty-state compact";
    const p = document.createElement("p");
    p.textContent = "No captured prompts yet.";
    const span = document.createElement("span");
    span.textContent = "Captured entries stay in this app session only.";
    empty.append(p, span);
    chatLog.append(empty);
    return;
  }
  state.prompts.slice().reverse().forEach((item) => {
    const entry = document.createElement("div");
    entry.className = "chat-entry";
    const meta = document.createElement("span");
    meta.textContent = item.time;
    const text = document.createElement("p");
    text.textContent = item.text;
    const note = document.createElement("small");
    note.textContent = "LOCAL CAPTURE · no AI response generated";
    entry.append(meta, text, note);
    chatLog.append(entry);
  });
}

function handlePrompt() {
  const text = promptInput.value.trim();
  if (!text) return;
  state.prompts.push({ text, time: nowLabel() });
  promptStatus.textContent = "Prompt captured locally. Orchestrator request wiring remains intentionally deferred; no AI response is being fabricated.";
  addTimelineEvent("Prompt captured locally", `“${text.slice(0, 100)}${text.length > 100 ? "…" : ""}”`);
  promptInput.value = "";
  renderPrompts();
}

const titles = { overview: "Command Center", chat: "Chat", tasks: "Tasks", memory: "Memory", devices: "Devices", approvals: "Approvals", settings: "Settings" };
function activateView(section) {
  if (!titles[section]) return;
  document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.section === section));
  document.querySelectorAll("[data-view]").forEach((view) => {
    const active = view.dataset.view === section;
    view.hidden = !active;
    view.classList.toggle("active", active);
  });
  document.getElementById("page-title").textContent = titles[section];
  if (state.startupBehavior === "remember") localStorage.setItem("syntra.lastView", section);
}

function populateSettings() {
  document.getElementById("gateway-base-input").value = state.gatewayBase;
  document.getElementById("runtime-port-input").value = String(state.runtimePort);
  document.getElementById("privacy-profile").value = state.privacyProfile;
  document.getElementById("startup-behavior").value = state.startupBehavior;
}

function saveSettings(event) {
  event.preventDefault();
  const gateway = normalizeLoopbackBase(document.getElementById("gateway-base-input").value);
  const runtimePort = Number.parseInt(document.getElementById("runtime-port-input").value, 10);
  if (!gateway) {
    showNotice("Gateway URL must be a loopback HTTP address.", "danger");
    return;
  }
  if (!Number.isInteger(runtimePort) || runtimePort < 1 || runtimePort > 65535) {
    showNotice("Runtime port must be between 1 and 65535.", "danger");
    return;
  }
  state.gatewayBase = gateway;
  state.runtimePort = runtimePort;
  state.privacyProfile = document.getElementById("privacy-profile").value;
  state.startupBehavior = document.getElementById("startup-behavior").value;
  localStorage.setItem("syntra.gatewayBase", state.gatewayBase);
  localStorage.setItem("syntra.runtimePort", String(state.runtimePort));
  localStorage.setItem("syntra.privacyProfile", state.privacyProfile);
  localStorage.setItem("syntra.startupBehavior", state.startupBehavior);
  showNotice("Local settings saved. No credentials were stored.");
  addTimelineEvent("Local settings updated", `Gateway ${state.gatewayBase}; runtime port ${state.runtimePort}; privacy ${state.privacyProfile}.`);
}

function resetSettings() {
  ["syntra.gatewayBase", "syntra.runtimePort", "syntra.privacyProfile", "syntra.startupBehavior", "syntra.lastView"].forEach((key) => localStorage.removeItem(key));
  Object.assign(state, DEFAULTS);
  populateSettings();
  showNotice("Local settings reset to safe defaults.");
}

document.querySelectorAll("[data-control]").forEach((button) => button.addEventListener("click", () => applyLocalControl(button.dataset.control)));
document.querySelectorAll(".nav-item").forEach((button) => button.addEventListener("click", () => activateView(button.dataset.section)));
document.getElementById("probe-gateway").addEventListener("click", probeGateway);
document.getElementById("probe-runtime").addEventListener("click", probeRuntime);
document.getElementById("send-prompt").addEventListener("click", handlePrompt);
promptInput.addEventListener("keydown", (event) => { if (event.key === "Enter") handlePrompt(); });
document.getElementById("settings-form").addEventListener("submit", saveSettings);
document.getElementById("reset-settings").addEventListener("click", resetSettings);

document.addEventListener("keydown", (event) => {
  if (!event.ctrlKey || event.altKey || event.metaKey) return;
  const sections = ["overview", "chat", "tasks", "memory", "devices", "approvals", "settings"];
  const index = Number.parseInt(event.key, 10) - 1;
  if (index >= 0 && index < sections.length) {
    event.preventDefault();
    activateView(sections[index]);
  }
});

populateSettings();
renderPrompts();
const initialView = state.startupBehavior === "remember" ? localStorage.getItem("syntra.lastView") || "overview" : "overview";
activateView(initialView);
updateClock();
setInterval(updateClock, 1000);
