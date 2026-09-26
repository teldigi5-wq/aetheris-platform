const state = {
  control: "NORMAL",
  gatewayBase: localStorage.getItem("syntra.gatewayBase") || "http://127.0.0.1:8080"
};

const controlState = document.getElementById("control-state");
const gatewayStatus = document.getElementById("gateway-status");
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

function handlePrompt() {
  const text = promptInput.value.trim();
  if (!text) return;

  promptStatus.textContent = "Prompt captured locally. Orchestrator request wiring is the next desktop slice; no AI response is being fabricated.";
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
document.getElementById("send-prompt").addEventListener("click", handlePrompt);
promptInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") handlePrompt();
});

updateClock();
setInterval(updateClock, 1000);
