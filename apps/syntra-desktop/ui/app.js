function loadRuntimePort() {
  const value = Number.parseInt(localStorage.getItem("syntra.runtimePort") || "8090", 10);
  return Number.isInteger(value) && value > 0 && value <= 65535 ? value : 8090;
}

const state = {
  control: "NORMAL",
  gatewayBase: localStorage.getItem("syntra.gatewayBase") || "http://127.0.0.1:8080",
  runtimePort: loadRuntimePort()
};

const controlState = document.getElementById("control-state");
const gatewayStatus = document.getElementById("gateway-status");
const runtimeStatus = document.getElementById("runtime-status");
const timeline = document.getElementById("timeline");
const clock = document.getElementById("clock");
const promptInput = document.getElementById("prompt-input");
const promptStatus = document.getElementById("prompt-status");

function nowLabel() {
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date());
}

function updateClock() {
  clock.textContent = nowLabel();
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

  while (timeline.children.length > 6) {
    timeline.removeChild(timeline.lastElementChild);
  }
}

function applyLocalControl(action) {
  const next = {
    STOP: "STOPPED",
    PAUSE: "PAUSED",
    RESUME: "NORMAL",
    TAKE_CONTROL: "OWNER_CONTROL"
  }[action];

  if (!next) return;
  state.control = next;
  controlState.textContent = next;
  addTimelineEvent(
    `Local control intent: ${action}`,
    "UI state changed locally only. No remote execution success is claimed until the authenticated control endpoint is wired and evidence confirms the result."
  );
}

async function probeGateway() {
  gatewayStatus.textContent = "Probing…";
  const candidates = [
    `${state.gatewayBase}/actuator/health`,
    `${state.gatewayBase}/api/actuator/health`
  ];

  for (const url of candidates) {
    try {
      const response = await fetch(url, { method: "GET", cache: "no-store" });
      if (!response.ok) continue;
      const body = await response.json().catch(() => ({}));
      const status = typeof body.status === "string" ? body.status : "REACHABLE";
      gatewayStatus.textContent = status;
      addTimelineEvent("Local gateway probe returned evidence", `${url} responded with HTTP ${response.status}; reported status: ${status}.`);
      return;
    } catch (_) {
      // A failed probe is expected when running without the owner workstation.
    }
  }

  gatewayStatus.textContent = "Offline / unavailable";
  addTimelineEvent(
    "Local gateway unavailable",
    `No supported health response was received from ${state.gatewayBase}. This is expected before the owner-PC stack is running.`
  );
}

async function probeRuntime() {
  runtimeStatus.textContent = "Probing…";
  const invoke = window.__TAURI__?.core?.invoke;

  if (typeof invoke !== "function") {
    runtimeStatus.textContent = "Native probe unavailable";
    addTimelineEvent(
      "Aetheris runtime probe unavailable",
      "This page is not running inside the native Syntra shell, so no runtime-health claim is made."
    );
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
    addTimelineEvent(
      "Aetheris runtime probe failed",
      `The native probe did not return usable evidence: ${String(error)}`
    );
  }
}

function handlePrompt() {
  const text = promptInput.value.trim();
  if (!text) return;

  promptStatus.textContent = "Prompt captured locally. Orchestrator request wiring remains intentionally deferred; no AI response is being fabricated.";
  addTimelineEvent("Prompt captured locally", `“${text.slice(0, 100)}${text.length > 100 ? "…" : ""}”`);
  promptInput.value = "";
}

document.querySelectorAll("[data-control]").forEach((button) => {
  button.addEventListener("click", () => applyLocalControl(button.dataset.control));
});

document.querySelectorAll(".nav-item").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll(".nav-item").forEach((item) => item.classList.remove("active"));
    button.classList.add("active");
    document.getElementById("page-title").textContent = button.textContent === "Overview" ? "Command Center" : button.textContent;
    addTimelineEvent(`${button.textContent} view selected`, "Navigation is active; feature-specific panels will be implemented incrementally.");
  });
});

document.getElementById("probe-gateway").addEventListener("click", probeGateway);
document.getElementById("probe-runtime").addEventListener("click", probeRuntime);
document.getElementById("send-prompt").addEventListener("click", handlePrompt);
promptInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") handlePrompt();
});

updateClock();
setInterval(updateClock, 1000);
