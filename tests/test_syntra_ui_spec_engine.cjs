"use strict";
const assert = require("node:assert/strict");
const {test} = require("node:test");
const engine = require("../apps/syntra-desktop/ui/creative-ui-spec-engine.js");

test("three palettes, two layouts and immutable validated defaults",()=>{
  assert.deepEqual(Object.keys(engine.PALETTES).sort(),["ivory","obsidian","ocean"]);
  assert.deepEqual(engine.LAYOUTS,["editorial","centered"]);
  assert.ok(Object.isFrozen(engine.normalize(engine.DEFAULTS)));
  assert.ok(Object.isFrozen(engine.PALETTES));
  for(const palette of Object.keys(engine.PALETTES))for(const layout of engine.LAYOUTS){
    const spec=engine.normalize({...engine.DEFAULTS,palette,layout});
    const html=engine.documentHTML(spec);
    assert.match(html,/<!doctype html>/i);
    assert.match(html,/<html lang="en">/);
    assert.match(html,/meta http-equiv="Content-Security-Policy"/);
    assert.match(html,/prefers-reduced-motion:reduce/);
    assert.match(html,/max-width:680px/);
    assert.doesNotMatch(html,/<script\b|<iframe\b|<link\b|@import\b|javascript:/i);
  }
});

test("malicious user copy renders only escaped inert text",()=>{
  const payload = '<img src=x onerror="alert(1)">&\'</style><script>alert(1)</script>';
  const html=engine.documentHTML({...engine.DEFAULTS,description:payload});
  assert.ok(html.includes("&lt;img src=x onerror=&quot;alert(1)&quot;&gt;"));
  assert.ok(html.includes("&lt;script&gt;alert(1)&lt;/script&gt;"));
  assert.ok(html.includes("&lt;/style&gt;"));
  assert.ok(html.includes("&amp;"));
  assert.ok(html.includes("&#39;"));
  assert.doesNotMatch(html,/<img\b|<script\b|<\/style>\s*<script|<iframe\b/i);
});

test("unexpected properties and arbitrary CSS/theme injection are rejected",()=>{
  for(const [field,value] of [
    ["palette","url(https://bad.invalid)"],["palette","__proto__"],["layout","<svg>"],
    ["action",""],["description","A".repeat(241)],["headline","B".repeat(81)],
    ["eyebrow","X\nInjected"],["action","X\u0000Y"]
  ]){
    assert.throws(()=>engine.documentHTML({...engine.DEFAULTS,[field]:value}),{name:/RangeError/});
  }
  assert.throws(()=>engine.documentHTML({...engine.DEFAULTS,arbitraryJs:"alert(1)"}),{name:"TypeError"});
  assert.throws(()=>engine.documentHTML(null),{name:"TypeError"});
});

test("all outputs are offline, external-asset-free and noninteractive markup",()=>{
  const html=engine.documentHTML(engine.DEFAULTS);
  assert.doesNotMatch(html,/\bhttps?:\/\/|<script\b|<link\b|<img\b|<form\b|<iframe\b|<object\b/i);
  assert.match(html,/default-src 'none'/);
  assert.match(html,/form-action 'none'/);
  assert.match(html,/static call to action preview/i);
  assert.match(html,/No live backend, trackers, scripts or external assets/i);
});

test("source compilation deterministic and does not mutate input",()=>{
  const input={...engine.DEFAULTS};
  const snapshot=JSON.stringify(input);
  const a=engine.documentHTML(input);
  const b=engine.documentHTML(input);
  assert.equal(a,b);
  assert.equal(JSON.stringify(input),snapshot);
  assert.ok(a.length>1700 && a.length<12000);
});


test("version 1 portable concept snapshot has deterministic validated round trip",()=>{
  const original={...engine.DEFAULTS,headline:"An editorial experience",palette:"ivory",layout:"centered"};
  const before=JSON.stringify(original);
  const json=engine.snapshotJSON(original);
  const wrapped=JSON.parse(json);
  assert.deepEqual(Object.keys(wrapped),["kind","schemaVersion","spec"]);
  assert.equal(wrapped.kind,"SYNTRA_STATIC_UI_CONCEPT");
  assert.equal(wrapped.schemaVersion,1);
  assert.deepEqual(engine.parseSnapshotJSON(json),engine.normalize(original));
  assert.equal(engine.snapshotJSON(engine.parseSnapshotJSON(json)),json);
  assert.equal(JSON.stringify(original),before);
  assert.ok(Object.isFrozen(engine.parseSnapshotJSON(json)));
  assert.ok(json.length<engine.SNAPSHOT_MAX_CHARS);
});

test("snapshot parser fails closed for wrong versions, structure, colors and oversized sources",()=>{
  const base=JSON.parse(engine.snapshotJSON(engine.DEFAULTS));
  const bad=[
    {...base,kind:"OTHER"},
    {...base,schemaVersion:2},
    {...base,extra:"script"},
    {...base,spec:{...base.spec,script:"alert(1)"}},
    {...base,spec:{...base.spec,palette:"https://bad.invalid"}},
    {...base,spec:{...base.spec,headline:""}},
    {...base,spec:{...base.spec,headline:"X\\nScript"}},
    {...base,spec:[base.spec]},
    {...base,spec:{headline:base.spec.headline}},
    {...base,spec:null},
    [],
    null
  ];
  for(const sample of bad) assert.throws(()=>engine.parseSnapshotJSON(JSON.stringify(sample)));
  for(const text of ["", "{broken", "null", "[]", "not json", " ".repeat(2049)]){
    assert.throws(()=>engine.parseSnapshotJSON(text));
  }
  assert.throws(()=>engine.parseSnapshotJSON({kind:"object"}),TypeError);
  assert.throws(()=>engine.parseSnapshotJSON(JSON.stringify({__proto__:null,kind:base.kind,schemaVersion:1,spec:base.spec,other:1})),TypeError);
});

test("imported attacker-controlled text stays inert after safe re-export",()=>{
  const value={...engine.DEFAULTS,headline:'<svg onload="alert(1)">',description:"<script>bad()</script>"};
  const portable=engine.snapshotJSON(value);
  const parsed=engine.parseSnapshotJSON(portable);
  const html=engine.documentHTML(parsed);
  assert.match(html,/&lt;svg onload=/);
  assert.match(html,/&lt;script&gt;bad\(\)&lt;\/script&gt;/);
  assert.doesNotMatch(html,/<svg\b|<script\b/i);
  const original=JSON.parse(portable);
  original.spec["__proto__"]="bad";
  original.spec.constructor="bad";
  assert.throws(()=>engine.parseSnapshotJSON(JSON.stringify(original)),TypeError);
});
