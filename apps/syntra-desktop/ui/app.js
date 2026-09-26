const DEFAULTS = Object.freeze({
  gatewayBase: "http://127.0.0.1:8080",
  runtimePort: 8090,
  privacyProfile: "private",
  startupBehavior: "manual"
});

const HEALTH = Object.freeze({ NOT_PROBED: "NOT_PROBED", UP: "UP", UNAVAILABLE: "UNAVAILABLE", DEGRADED: "DEGRADED", ERROR: "ERROR" });

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
  prompts: [],
  gatewayState: HEALTH.NOT_PROBED,
  runtimeState: HEALTH.NOT_PROBED,
  lastRecoveryCheck: null
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
const diagnosticsPreview = document.getElementById("diagnostics-preview");
const diagnosticsPath = document.getElementById("diagnostics-path");
const recoveryGateway = document.getElementById("recovery-gateway");
const recoveryRuntime = document.getElementById("recovery-runtime");
const recoveryChecked = document.getElementById("recovery-checked");

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

function refreshRecoveryPanel() {
  recoveryGateway.textContent = state.gatewayState;
  recoveryRuntime.textContent = state.runtimeState;
  recoveryChecked.textContent = state.lastRecoveryCheck || "Not run";
}

async function probeGateway() {
  gatewayStatus.textContent = "Probing…";
  const candidates = [`${state.gatewayBase}/actuator/health`, `${state.gatewayBase}/api/actuator/health`];
  for (const url of candidates) {
    try {
      const response = await fetch(url, { method: "GET", cache: "no-store" });
      if (!response.ok) {
        state.gatewayState = HEALTH.DEGRADED;
        continue;
      }
      const body = await response.json().catch(() => ({}));
      const status = typeof body.status === "string" ? body.status : "REACHABLE";
      state.gatewayState = status === "UP" || status === "REACHABLE" ? HEALTH.UP : HEALTH.DEGRADED;
      gatewayStatus.textContent = status;
      addTimelineEvent("Local gateway probe returned evidence", `${url} responded with HTTP ${response.status}; reported status: ${status}.`);
      refreshRecoveryPanel();
      return state.gatewayState;
    } catch (_) {}
  }
  state.gatewayState = HEALTH.UNAVAILABLE;
  gatewayStatus.textContent = "Offline / unavailable";
  addTimelineEvent("Local gateway unavailable", `No supported health response was received from ${state.gatewayBase}. This is expected before the owner-PC stack is running.`);
  refreshRecoveryPanel();
  return state.gatewayState;
}

async function probeRuntime() {
  runtimeStatus.textContent = "Probing…";
  const invoke = window.__TAURI__?.core?.invoke;
  if (typeof invoke !== "function") {
    state.runtimeState = HEALTH.ERROR;
    runtimeStatus.textContent = "Native probe unavailable";
    addTimelineEvent("Aetheris runtime probe unavailable", "This page is not running inside the native Syntra shell, so no runtime-health claim is made.");
    refreshRecoveryPanel();
    return state.runtimeState;
  }
  try {
    const result = await invoke("probe_aetheris_runtime", { port: state.runtimePort });
    const reportedStatus = typeof result.reportedStatus === "string" ? result.reportedStatus : "UNKNOWN";
    const httpStatus = Number.isInteger(result.httpStatus) ? result.httpStatus : null;
    if (result.healthy === true) {
      state.runtimeState = HEALTH.UP;
      runtimeStatus.textContent = `${reportedStatus} · HTTP ${httpStatus}`;
      addTimelineEvent("Aetheris runtime health verified", result.evidence);
    } else if (result.reachable === true) {
      state.runtimeState = HEALTH.DEGRADED;
      runtimeStatus.textContent = `${reportedStatus} · HTTP ${httpStatus ?? "?"}`;
      addTimelineEvent("Aetheris runtime responded but is not healthy", result.evidence);
    } else {
      state.runtimeState = HEALTH.UNAVAILABLE;
      runtimeStatus.textContent = "Offline / unavailable";
      addTimelineEvent("Aetheris runtime unavailable", result.evidence);
    }
  } catch (error) {
    state.runtimeState = HEALTH.ERROR;
    runtimeStatus.textContent = "Probe failed";
    addTimelineEvent("Aetheris runtime probe failed", `The native probe did not return usable evidence: ${String(error)}`);
  }
  refreshRecoveryPanel();
  return state.runtimeState;
}

async function runRecoveryCheck() {
  const button = document.getElementById("run-recovery-check");
  button.disabled = true;
  button.textContent = "Checking…";
  try {
    await Promise.all([probeGateway(), probeRuntime()]);
    state.lastRecoveryCheck = new Date().toISOString();
    refreshRecoveryPanel();
    const healthy = state.gatewayState === HEALTH.UP && state.runtimeState === HEALTH.UP;
    showNotice(healthy ? "Local connectivity checks are healthy." : "Recovery check completed with unavailable or degraded components.", healthy ? "info" : "danger");
    addTimelineEvent("Recovery check completed", `Gateway ${state.gatewayState}; runtime ${state.runtimeState}. No unavailable component was treated as healthy.`);
  } finally {
    button.disabled = false;
    button.textContent = "Run recovery check";
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
  addTimelineEvent("Prompt captured locally", `Local prompt staging recorded ${text.length} characters. Prompt content is omitted from diagnostics and the event timeline.`);
  promptInput.value = "";
  renderPrompts();
}

function clearLocalSession() {
  state.prompts = [];
  renderPrompts();
  showNotice("Local staged prompts cleared.");
  addTimelineEvent("Local chat staging cleared", "Prompt content was removed from the current UI session. No backend memory deletion is claimed.");
}

const titles = { overview: "Command Center", chat: "Chat", tasks: "Tasks", memory: "Memory", devices: "Devices", approvals: "Approvals", settings: "Settings", diagnostics: "Diagnostics & Recovery" };
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
  state.gatewayState = HEALTH.NOT_PROBED;
  state.runtimeState = HEALTH.NOT_PROBED;
  state.lastRecoveryCheck = null;
  refreshRecoveryPanel();
  showNotice("Local settings saved. No credentials were stored.");
  addTimelineEvent("Local settings updated", `Gateway remains loopback-only; runtime port ${state.runtimePort}; privacy profile ${state.privacyProfile}.`);
}

function resetSettings() {
  ["syntra.gatewayBase", "syntra.runtimePort", "syntra.privacyProfile", "syntra.startupBehavior", "syntra.lastView"].forEach((key) => localStorage.removeItem(key));
  state.gatewayBase = DEFAULTS.gatewayBase;
  state.runtimePort = DEFAULTS.runtimePort;
  state.privacyProfile = DEFAULTS.privacyProfile;
  state.startupBehavior = DEFAULTS.startupBehavior;
  state.gatewayState = HEALTH.NOT_PROBED;
  state.runtimeState = HEALTH.NOT_PROBED;
  state.lastRecoveryCheck = null;
  populateSettings();
  refreshRecoveryPanel();
  showNotice("Local settings reset to safe defaults.");
}

function buildDiagnosticsPayload() {
  return {
    controlState: state.control,
    gatewayBase: state.gatewayBase,
    runtimePort: state.runtimePort,
    gatewayState: state.gatewayState,
    runtimeState: state.runtimeState,
    privacyProfile: state.privacyProfile,
    startupBehavior: state.startupBehavior,
    promptCount: state.prompts.length
  };
}

function renderDiagnosticsPreview() {
  const preview = {
    schemaVersion: "1",
    syntraVersion: "0.4.0",
    physicalPcStatus: "BLOCKED_PENDING_HARDWARE",
    backendCommandWiringStatus: "not-wired-no-success-claims",
    ...buildDiagnosticsPayload(),
    secretMaterialIncluded: false,
    promptContentIncluded: false
  };
  diagnosticsPreview.textContent = JSON.stringify(preview, null, 2);
  return preview;
}

async function exportDiagnostics() {
  renderDiagnosticsPreview();
  const invoke = window.__TAURI__?.core?.invoke;
  if (typeof invoke !== "function") {
    diagnosticsPath.textContent = "Native export unavailable outside Syntra.exe; sanitized preview generated only.";
    showNotice("Diagnostics preview generated; native file export requires Syntra.exe.");
    return;
  }
  try {
    const result = await invoke("export_diagnostics", { payload: buildDiagnosticsPayload() });
    diagnosticsPath.textContent = result.path;
    showNotice("Sanitized diagnostics report exported.");
    addTimelineEvent("Diagnostics exported", "A sanitized report was written without prompt content, credentials, tokens, passwords, or API keys.");
  } catch (error) {
    diagnosticsPath.textContent = `Export failed: ${String(error)}`;
    showNotice("Diagnostics export failed closed.", "danger");
  }
}

document.querySelectorAll("[data-control]").forEach((button) => button.addEventListener("click", () => applyLocalControl(button.dataset.control)));
document.querySelectorAll(".nav-item").forEach((button) => button.addEventListener("click", () => activateView(button.dataset.section)));
document.getElementById("probe-gateway").addEventListener("click", probeGateway);
document.getElementById("probe-runtime").addEventListener("click", probeRuntime);
document.getElementById("send-prompt").addEventListener("click", handlePrompt);
promptInput.addEventListener("keydown", (event) => { if (event.key === "Enter") handlePrompt(); });
document.getElementById("settings-form").addEventListener("submit", saveSettings);
document.getElementById("reset-settings").addEventListener("click", resetSettings);
document.getElementById("run-recovery-check").addEventListener("click", runRecoveryCheck);
document.getElementById("clear-local-session").addEventListener("click", clearLocalSession);
document.getElementById("export-diagnostics").addEventListener("click", exportDiagnostics);
document.getElementById("refresh-diagnostics-preview").addEventListener("click", renderDiagnosticsPreview);

document.addEventListener("keydown", (event) => {
  if (!event.ctrlKey || event.altKey || event.metaKey) return;
  const sections = ["overview", "chat", "tasks", "memory", "devices", "approvals", "settings", "diagnostics"];
  const index = Number.parseInt(event.key, 10) - 1;
  if (index >= 0 && index < sections.length) {
    event.preventDefault();
    activateView(sections[index]);
  }
});

populateSettings();
renderPrompts();
refreshRecoveryPanel();
renderDiagnosticsPreview();
const initialView = state.startupBehavior === "remember" ? localStorage.getItem("syntra.lastView") || "overview" : "overview";
activateView(initialView);
updateClock();
setInterval(updateClock, 1000);
