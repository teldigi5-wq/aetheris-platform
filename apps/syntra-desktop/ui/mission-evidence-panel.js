/* User-selected offline Aetheris TaskEvent JSON inspector.
 * Never opens a backend connection, obtains credentials or creates tasks.
 * Imported data is untrusted and NOT evidence of an authenticated live run.
 */
(() => {
  "use strict";
  const api = window.SyntraMissionEvidence;
  const input = document.getElementById("syntra-mission-evidence-file");
  const status = document.getElementById("syntra-mission-evidence-status");
  const summary = document.getElementById("syntra-mission-evidence-summary");
  const list = document.getElementById("syntra-mission-evidence-list");
  if (!api || !input || !status || !summary || !list) return;
  const MAX_BYTES = 256 * 1024;
  const MAX_EVENTS = 500;
  let generation = 0;
  const setStatus = value => { status.textContent = value; };
  function record(parent, name, value, note) {
    const node = document.createElement("div");
    const title = document.createElement("strong");
    const label = document.createElement("span");
    const foot = document.createElement("small");
    title.textContent = name;
    label.textContent = value;
    foot.textContent = note;
    node.append(title, label, foot);
    parent.append(node);
  }
  function blank() {
    summary.replaceChildren();
    list.replaceChildren();
    record(summary, "Connection", "Not connected", "Verified native authentication required");
    record(summary, "Verified live actions", "None", "Offline data never grants execution authority");
  }
  function render(snapshot) {
    const result = api.summary(snapshot);
    summary.replaceChildren();
    list.replaceChildren();
    record(summary, "Connection", "Not connected", "No native authenticated event source");
    record(summary, "Offline task IDs", String(result.taskCount), "Imported and unverified; not live execution");
    record(summary, "Running (imported)", String(result.counts.RUNNING), "Unverified state in user-selected file");
    record(summary, "Completed (imported)", String(result.counts.COMPLETED), "Not proof of task success");
    for (const task of snapshot.tasks.slice(0, 20)) {
      const item = document.createElement("div");
      item.className = "syntra-mission-evidence-item";
      const state = document.createElement("strong");
      const description = document.createElement("span");
      state.textContent = task.state;
      description.textContent = "Task …" + task.taskId.slice(-8) +
        " · Agent " + (task.agentId || "unspecified") + " · " + task.updatedAt;
      item.append(state, description);
      list.append(item);
    }
  }
  blank();
  input.addEventListener("change", async () => {
    const myGeneration = ++generation;
    blank();
    const file = input.files && input.files[0];
    if (!file) { setStatus("No file selected. Mission stream still disconnected."); return; }
    if (!Number.isFinite(file.size) || file.size < 0 || file.size > MAX_BYTES ||
        typeof file.text !== "function") {
      setStatus("Import rejected: only local JSON files up to 256 KB are supported.");
      return;
    }
    setStatus("Reading selected file locally. No network or task action will occur.");
    try {
      const content = await file.text();
      if (myGeneration !== generation) return;
      if (typeof content !== "string" || content.length > MAX_BYTES) {
        setStatus("Import rejected: oversized file."); return;
      }
      const parsed = JSON.parse(content);
      if (!Array.isArray(parsed) || parsed.length > MAX_EVENTS) {
        setStatus("Import rejected: expected an array of at most 500 task events."); return;
      }
      let snapshot = api.emptySnapshot(), accepted = 0, rejected = 0;
      for (const event of parsed) {
        const next = api.applyEvent(snapshot, event);
        if (next === snapshot) rejected += 1;
        else accepted += 1;
        snapshot = next;
      }
      render(snapshot);
      setStatus("OFFLINE IMPORT · UNVERIFIED: " + accepted + " accepted event updates, " +
        rejected + " ignored/invalid updates. Native mission stream is disconnected.");
    } catch (_) {
      if (myGeneration === generation) {
        blank();
        setStatus("Import failed. File was not accepted; no data was sent or stored.");
      }
    }
  });
})();
