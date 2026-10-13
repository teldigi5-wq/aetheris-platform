/* Syntra mission event evidence reducer.
 * Matches external Aetheris runtime TaskEvent and TaskState source definitions.
 * This module NEVER connects to a network, obtains tokens or claims execution.
 * Data accepted here is NOT proof of a trusted origin: a future native
 * authenticated session must validate source and scope before calling it.
 */
(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else if (root) root.SyntraMissionEvidence = api;
})(typeof window !== "undefined" ? window : undefined, function () {
  "use strict";
  const STATES = Object.freeze([
    "QUEUED", "PLANNING", "AWAITING_APPROVAL", "RUNNING",
    "VERIFYING", "PAUSED", "ROLLING_BACK",
    "COMPLETED", "FAILED", "CANCELLED"
  ]);
  const TERMINAL = new Set(["COMPLETED", "FAILED", "CANCELLED"]);
  const MAX_TASKS = 48;
  const MAX_RECENT = 96;
  const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const ISO_INSTANT = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$/;
  const AGENT = /^[a-z0-9][a-z0-9-]{0,79}$/;
  const STATUSES = Object.freeze({
    disconnected: "DISCONNECTED_AUTH_REQUIRED",
    pending: "AWAITING_VERIFIED_RUNTIME_EVENTS"
  });
  function normalizedTime(value) {
    if (typeof value !== "string" || value.length > 40) return null;
    const match = ISO_INSTANT.exec(value);
    if (!match || !Number.isFinite(Date.parse(match[1] + "Z"))) return null;
    const fraction = (match[2] || "").padEnd(9, "0");
    return match[1] + "." + fraction + "Z";
  }
  function validatedEvent(event) {
    if (!event || typeof event !== "object" || Array.isArray(event)) return null;
    const timeKey = normalizedTime(event.timestamp);
    if (typeof event.taskId !== "string" || !UUID.test(event.taskId) ||
        !timeKey || !STATES.includes(event.state) ||
        (event.agentId != null && (typeof event.agentId !== "string" ||
          !AGENT.test(event.agentId)))) return null;
    // SECURITY: intentionally discard message, commandText and metadata,
    // which may contain secrets, external instructions or personal data.
    return Object.freeze({
      taskId: event.taskId.toLowerCase(),
      timestamp: event.timestamp,
      timeKey,
      state: event.state,
      agentId: event.agentId == null ? null : event.agentId
    });
  }
  function frozenSnapshot(tasks, recent) {
    return Object.freeze({
      connection: STATUSES.disconnected,
      authenticated: false,
      canExecute: false,
      pendingBackendWiring: true,
      taskCount: tasks.length,
      tasks: Object.freeze(tasks.map(t => Object.freeze(t))),
      recent: Object.freeze(recent.map(e => Object.freeze(e)))
    });
  }
  function emptySnapshot() { return frozenSnapshot([], []); }
  function applyEvent(snapshot, event) {
    if (!snapshot || snapshot.connection !== STATUSES.disconnected ||
        snapshot.authenticated !== false || snapshot.canExecute !== false ||
        !Array.isArray(snapshot.tasks) || !Array.isArray(snapshot.recent) ||
        snapshot.tasks.length > MAX_TASKS || snapshot.recent.length > MAX_RECENT)
      throw new TypeError("Mission evidence reducer requires a blocked snapshot.");
    const clean = validatedEvent(event);
    if (!clean) return snapshot;
    const original = snapshot.tasks.find(t => t.taskId === clean.taskId);
    if (original && (clean.timeKey <= original.timeKey || TERMINAL.has(original.state)))
      return snapshot;
    const next = Object.freeze({
      taskId: clean.taskId, state: clean.state, agentId: clean.agentId,
      updatedAt: clean.timestamp, timeKey: clean.timeKey
    });
    const tasks = [next, ...snapshot.tasks.filter(t => t.taskId !== clean.taskId)].slice(0, MAX_TASKS);
    const recent = [clean, ...snapshot.recent].slice(0, MAX_RECENT);
    return frozenSnapshot(tasks, recent);
  }
  function summary(snapshot) {
    if (!snapshot || !Array.isArray(snapshot.tasks)) throw new TypeError("Invalid mission snapshot");
    const counts = Object.fromEntries(STATES.map(s => [s, 0]));
    for (const task of snapshot.tasks) if (STATES.includes(task.state)) counts[task.state] += 1;
    return Object.freeze({
      source: "CONTRACT_ONLY_NOT_AUTHENTICATED",
      connected: false,
      text: "Mission updates require a verified native authenticated session. No live tasks are claimed.",
      taskCount: snapshot.taskCount,
      counts: Object.freeze(counts)
    });
  }
  return Object.freeze({ STATES, STATUSES, MAX_TASKS, MAX_RECENT,
    normalizedTime, validatedEvent, emptySnapshot, applyEvent, summary });
});
