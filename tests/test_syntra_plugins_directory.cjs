/* Dependency-free UI behavior verification with an isolated synthetic DOM. */
"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");

const root = path.resolve(__dirname, "../apps/syntra-desktop/ui");
class Element {
  constructor(tag = "div") {
    this.tagName = tag;
    this.className = "";
    this.value = "";
    this.children = [];
    this.handlers = {};
    this.attributes = {};
    this.disabled = false;
    this._textContent = "";
  }
  get firstChild() { return this.children.length ? this.children[0] : null; }
  set textContent(value) { this._textContent = String(value); this.children = []; }
  get textContent() { return this._textContent + this.children.map(x => x.textContent).join(""); }
  appendChild(item) { this.children.push(item); return item; }
  append(...items) { items.forEach(x => this.appendChild(x)); }
  removeChild(item) { const index = this.children.indexOf(item); if (index >= 0) this.children.splice(index, 1); }
  addEventListener(name, handler) { this.handlers[name] = handler; }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  trigger(name) { assert.equal(typeof this.handlers[name], "function"); return this.handlers[name](); }
}
function createEnvironment() {
  const ids = ["extension-list", "extension-review", "extension-search", "extension-kind",
    "extension-category", "extension-results-count", "extension-clear", "extension-live-status"];
  const nodes = Object.fromEntries(ids.map(id => [id, new Element()]));
  nodes["extension-kind"].value = "all";
  nodes["extension-category"].value = "all";
  const sandbox = {
    window: {}, URL,
    navigator: { clipboard: { writeText: async () => { throw Error("Mock clipboard disabled"); } } },
    document: {
      getElementById(id) { return nodes[id]; },
      createElement(tag) { return new Element(tag); }
    }
  };
  vm.runInNewContext(fs.readFileSync(path.join(root, "plugins-catalog.js"), "utf8"), sandbox);
  vm.runInNewContext(fs.readFileSync(path.join(root, "plugins-directory.js"), "utf8"), sandbox);
  return { nodes, sandbox };
}

test("render 25 catalog entries and zero installed", () => {
  const { nodes } = createEnvironment();
  assert.equal(nodes["extension-list"].children.length, 25);
  assert.match(nodes["extension-results-count"].textContent, /25 of 25 reference entries.*0 installed/);
  assert.equal(typeof nodes["extension-kind"].handlers.input, "function");
});

test("filters: five skill templates, two local-memory projects, reset to all", () => {
  const { nodes } = createEnvironment();
  nodes["extension-kind"].value = "skill";
  nodes["extension-kind"].trigger("input");
  assert.equal(nodes["extension-list"].children.length, 5);
  nodes["extension-kind"].value = "all";
  nodes["extension-category"].value = "local-memory";
  nodes["extension-category"].trigger("input");
  assert.equal(nodes["extension-list"].children.length, 2);
  nodes["extension-search"].value = "n8n";
  nodes["extension-search"].trigger("input");
  assert.match(nodes["extension-results-count"].textContent, /0 of 25 reference entries/);
  assert.equal(nodes["extension-list"].children.length, 1);
  assert.match(nodes["extension-list"].children[0].textContent, /No matching references/);
  nodes["extension-clear"].trigger("click");
  assert.equal(nodes["extension-list"].children.length, 25);
});

test("selecting and comparing a source is session-only, not installation", () => {
  const { nodes } = createEnvironment();
  const firstCard = nodes["extension-list"].children[0];
  const reviewButton = firstCard.children.at(-1).children.at(-1);
  reviewButton.trigger("click");
  assert.match(nodes["extension-review"].textContent, /NOT INSTALLED/);
  assert.match(nodes["extension-review"].textContent, /Potential permissions.*NOT GRANTED/);
  assert.match(nodes["extension-review"].textContent, /Source for independent review/);
  const compareButton = nodes["extension-review"].children.at(-1);
  assert.match(compareButton.textContent, /Add to comparison/);
  compareButton.trigger("click");
  assert.match(nodes["extension-live-status"].textContent, /saved only in this app session/);
  assert.match(nodes["extension-results-count"].textContent, /0 installed/);
  assert.match(nodes["extension-review"].textContent, /NOT CONNECTED/);
});

test("invalid or tampered catalog disables directory and refuses activation", () => {
  const { nodes, sandbox } = createEnvironment();
  const catalog = sandbox.window.SyntraExtensionCatalog;
  assert.ok(Object.isFrozen(catalog));
  assert.ok(catalog.entries.every(entry => Object.isFrozen(entry)));
  assert.throws(() => { catalog.entries[0].enabled = true; }, TypeError);
  const bad = JSON.parse(JSON.stringify(catalog));
  bad.entries[0].enabled = true;
  const freshNodes = Object.fromEntries(Object.keys(nodes).map(id => [id, new Element()]));
  freshNodes["extension-kind"].value = "all";
  freshNodes["extension-category"].value = "all";
  const badCtx = {
    window: { SyntraExtensionCatalog: bad }, URL, navigator: {},
    document: { getElementById: id => freshNodes[id], createElement: t => new Element(t) }
  };
  vm.runInNewContext(fs.readFileSync(path.join(root, "plugins-directory.js"), "utf8"), badCtx);
  assert.match(freshNodes["extension-list"].textContent, /failed closed/);
  assert.equal(freshNodes["extension-search"].disabled, true);
  assert.equal(freshNodes["extension-kind"].disabled, true);
});
