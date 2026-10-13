/* Syntra offline extensions research view. No fetching, installation, imports, or backend calls. */
(() => {
  "use strict";

  const catalog = window.SyntraExtensionCatalog;
  const list = document.getElementById("extension-list");
  const details = document.getElementById("extension-review");
  const search = document.getElementById("extension-search");
  const kind = document.getElementById("extension-kind");
  const category = document.getElementById("extension-category");
  const count = document.getElementById("extension-results-count");
  const clear = document.getElementById("extension-clear");
  const status = document.getElementById("extension-live-status");
  const marked = new Set();
  let selectedId = null;

  const sourceHosts = new Set([
    "github.com", "agentskills.io", "registry.modelcontextprotocol.io",
    "pypi.org", "www.npmjs.com", "huggingface.co", "ollama.com"
  ]);
  const fields = ["repository", "skill", "directory"];
  function validCatalog() {
    return catalog && catalog.schemaVersion === 1
      && catalog.catalogMode === "SNAPSHOT_READ_ONLY"
      && catalog.physicalPcStatus === "BLOCKED_PENDING_HARDWARE"
      && catalog.noExternalFetch === true
      && catalog.automaticInstallAllowed === false && catalog.executionAllowed === false
      && Array.isArray(catalog.entries) && catalog.entries.length === 25
      && catalog.entries.filter((entry) => entry.type === "repository").length === 14
      && catalog.entries.filter((entry) => entry.type === "skill").length === 5
      && catalog.entries.filter((entry) => entry.type === "directory").length === 6
      && new Set(catalog.entries.map((entry) => entry.id)).size === 25
      && catalog.entries.every((entry) => {
        try {
          const url = new URL(entry.sourceUrl);
          return fields.includes(entry.type)
            && url.protocol === "https:" && sourceHosts.has(url.hostname)
            && !url.username && !url.password && !url.search && !url.hash
            && entry.installed === false && entry.enabled === false
            && entry.executionEnabled === false
            && entry.sourceRevisionReviewed === false && entry.licenseReview === "NOT_REVIEWED"
            && ["REFERENCE_ONLY", "TEMPLATE_ONLY"].includes(entry.status)
            && typeof entry.name === "string" && typeof entry.description === "string"
            && Array.isArray(entry.permissionReview) && Array.isArray(entry.capabilityHints);
        } catch (_) {
          return false;
        }
      });
  }

  function element(tag, className, content) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (content !== undefined) node.textContent = String(content);
    return node;
  }

  function clearChildren(node) {
    while (node.firstChild) node.removeChild(node.firstChild);
  }

  function createTag(value, extraClass = "") {
    return element("span", "extension-tag " + extraClass, value);
  }

  function renderFacts(parent, label, values) {
    const section = element("section", "extension-facts");
    section.appendChild(element("h4", "", label));
    const ul = element("ul", "");
    for (const value of values) {
      const li = element("li", "", value);
      ul.appendChild(li);
    }
    section.appendChild(ul);
    parent.appendChild(section);
  }

  function selectSource(id) {
    const entry = catalog.entries.find((source) => source.id === id);
    if (!entry) return;
    selectedId = id;
    clearChildren(details);
    const header = element("div", "extension-review-head");
    const heading = element("h3", "", entry.name);
    header.append(heading, createTag(entry.status, "is-muted"));
    details.appendChild(header);
    details.appendChild(element("p", "extension-review-desc", entry.description));
    const truth = element("div", "extension-trust-state");
    truth.append(
      createTag("NOT INSTALLED"),
      createTag("NOT CONNECTED"),
      createTag("LICENSE UNREVIEWED")
    );
    details.appendChild(truth);
    const dl = element("dl", "extension-metadata");
    const rows = [
      ["Type", entry.type], ["Category", entry.category],
      ["Reported license", entry.reportedLicense],
      ["Policy", "Owner permission required before installation"],
      ["Status", "Read-only catalog reference; no runtime authority"],
    ];
    for (const [key, value] of rows) {
      dl.append(element("dt", "", key), element("dd", "", value));
    }
    details.appendChild(dl);
    renderFacts(details, "Candidate capability — NOT ENABLED", entry.capabilityHints);
    renderFacts(details, "Potential permissions — NOT GRANTED", entry.permissionReview);
    const source = element("div", "extension-source");
    source.appendChild(element("span", "", "Source for independent review"));
    source.appendChild(element("code", "", entry.sourceUrl));
    const copy = element("button", "ghost-button extension-copy", "Copy source URL");
    copy.type = "button";
    copy.addEventListener("click", async () => {
      try {
        if (!navigator.clipboard?.writeText) throw new Error("Clipboard not available");
        await navigator.clipboard.writeText(entry.sourceUrl);
        status.textContent = "Source URL copied. No extension was installed or opened.";
      } catch (_) {
        status.textContent = "Clipboard unavailable. The full source URL is shown for manual copying.";
      }
    });
    source.appendChild(copy);
    details.appendChild(source);
    const note = element("p", "extension-review-warning",
      "A listing or read-only template is not an approved plugin. Source revision, security, transitive dependencies, model assets, scopes and license must be reviewed separately before any owner-authorized activation.");
    details.appendChild(note);
    const save = element("button", "ghost-button extension-save", marked.has(id) ? "Remove from comparison" : "Add to comparison");
    save.type = "button";
    save.addEventListener("click", () => {
      if (marked.has(id)) marked.delete(id);
      else if (marked.size < 6) marked.add(id);
      else {
        status.textContent = "Comparison is limited to six entries per session.";
        return;
      }
      status.textContent = marked.size + " comparison selections saved only in this app session. No installation requested.";
      render();
    });
    details.appendChild(save);
  }

  function filteredEntries() {
    const text = search.value.trim().toLocaleLowerCase();
    return catalog.entries.filter((entry) => {
      if (kind.value !== "all" && entry.type !== kind.value) return false;
      if (category.value !== "all" && entry.category !== category.value) return false;
      const searchable = [entry.name, entry.description, entry.category, entry.reportedLicense, entry.sourceUrl]
        .join(" ").toLocaleLowerCase();
      return !text || searchable.includes(text);
    });
  }

  function render() {
    const matches = filteredEntries();
    clearChildren(list);
    count.textContent = matches.length + " of 25 reference entries · 0 installed";
    if (!matches.length) {
      list.appendChild(element("p", "extension-empty", "No matching references. Change the search or filters. Nothing was installed."));
    }
    for (const entry of matches) {
      const card = element("article", "extension-result");
      const top = element("div", "extension-result-top");
      top.append(createTag(entry.type), createTag(marked.has(entry.id) ? "COMPARE LATER" : "REVIEW ONLY", "is-muted"));
      card.appendChild(top);
      card.appendChild(element("h3", "", entry.name));
      card.appendChild(element("p", "", entry.description));
      const bottom = element("div", "extension-result-bottom");
      bottom.appendChild(element("span", "", "License metadata: " + entry.reportedLicense));
      const review = element("button", "ghost-button", selectedId === entry.id ? "Viewing details" : "Review details");
      review.type = "button";
      review.setAttribute("aria-label", "Review " + entry.name + " permissions and license");
      review.setAttribute("aria-pressed", String(selectedId === entry.id));
      review.addEventListener("click", () => { selectSource(entry.id); render(); });
      bottom.appendChild(review);
      card.appendChild(bottom);
      list.appendChild(card);
    }
    if (selectedId !== null) {
      const source = catalog.entries.find((item) => item.id === selectedId);
      if (source) selectSource(source.id);
    }
  }

  if (!list || !details || !search || !kind || !category || !count || !clear || !status) return;
  if (!validCatalog()) {
    list.textContent = "Extension research catalog failed closed: unsupported source or trust metadata.";
    details.textContent = "Unavailable. Do not install or activate extensions from this view.";
    count.textContent = "Catalog validation failed";
    for (const input of [search, kind, category, clear]) input.disabled = true;
    return;
  }

  const categories = [...new Set(catalog.entries.map((entry) => entry.category))].sort();
  for (const value of categories) {
    const option = element("option", "", value.replaceAll("-", " "));
    option.value = value;
    category.appendChild(option);
  }
  for (const input of [search, kind, category]) input.addEventListener("input", render);
  clear.addEventListener("click", () => {
    search.value = "";
    kind.value = "all";
    category.value = "all";
    status.textContent = "Search filters reset. No changes to system permissions.";
    render();
  });
  render();
})();
