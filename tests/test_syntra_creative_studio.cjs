"use strict";
/* Dependency-free, synthetic-DOM UI regression tests. Nothing executes a plugin. */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const {test}=require("node:test");
const root=path.resolve(__dirname,"../apps/syntra-desktop/ui");
class E {
  constructor(tag="div"){this.tagName=tag;this.children=[];this.handlers={};this.attributes={};this.value="";this.disabled=false;this._text="";}
  get firstChild(){return this.children[0]||null;}
  set textContent(value){this._text=String(value);this.children=[];}
  get textContent(){return this._text+this.children.map(c=>c.textContent).join("");}
  append(...xs){this.children.push(...xs);}
  appendChild(x){this.children.push(x);return x;}
  removeChild(x){const i=this.children.indexOf(x);if(i>=0)this.children.splice(i,1);}
  setAttribute(k,v){this.attributes[k]=String(v);}
  addEventListener(name,fn){this.handlers[name]=fn;}
  trigger(name,ev={preventDefault(){}}){assert.equal(typeof this.handlers[name],"function");return this.handlers[name](ev);}
}
function setup(tamper){
  const ids=["creative-skill-list","creative-sources","creative-source-search","creative-source-category",
    "creative-subject","creative-plan","creative-source-count","creative-status","creative-clear",
    "creative-form","creative-plan-submit","creative-selected-title","creative-selected-summary","creative-deliverables"];
  const nodes=Object.fromEntries(ids.map(id=>[id,new E()]));
  nodes["creative-source-category"].value="all";
  const ctx={window:{},document:{getElementById:k=>nodes[k],createElement:t=>new E(t)}};
  vm.runInNewContext(fs.readFileSync(path.join(root,"creative-catalog.js"),"utf8"),ctx);
  if(tamper) {
    const raw=JSON.parse(JSON.stringify(ctx.window.SyntraCreativeCatalog));
    tamper(raw);
    ctx.window={SyntraCreativeCatalog:raw};
  }
  vm.runInNewContext(fs.readFileSync(path.join(root,"creative-workbench.js"),"utf8"),ctx);
  return {nodes,ctx};
}
test("renders five reviewer cards and twelve uninstalled library references",()=>{
  const {nodes,ctx}=setup();
  assert.equal(nodes["creative-skill-list"].children.length,5);
  assert.equal(nodes["creative-sources"].children.length,12);
  assert.match(nodes["creative-source-count"].textContent,/12 of 12 references/);
  assert.equal(ctx.window.SyntraCreativeCatalog.executionEnabled,false);
  assert.ok(Object.isFrozen(ctx.window.SyntraCreativeCatalog));
});
test("discipline choice changes review checklists but never runs tools",()=>{
  const {nodes}=setup();
  const third=nodes["creative-skill-list"].children[2];
  third.trigger("click");
  assert.equal(nodes["creative-selected-title"].textContent,"3D Experiences");
  assert.match(nodes["creative-deliverables"].textContent,/Scene graph/);
  assert.match(nodes["creative-status"].textContent,/not an active AI agent/);
});
test("prepares an offline brief and then clears session information",()=>{
  const {nodes}=setup();
  nodes["creative-subject"].value="Premium accessible 3D property site";
  nodes["creative-form"].trigger("submit");
  assert.match(nodes["creative-plan"].textContent,/Premium accessible 3D property site/);
  assert.match(nodes["creative-plan"].textContent,/No AI analysis/);
  assert.match(nodes["creative-status"].textContent,/No agent executed/);
  nodes["creative-clear"].trigger("click");
  assert.equal(nodes["creative-subject"].value,"");
  assert.doesNotMatch(nodes["creative-plan"].textContent,/Premium accessible 3D property site/);
});
test("rejects invalid and too-long briefs without fabricating a result",()=>{
  const {nodes}=setup();
  nodes["creative-subject"].value="";
  nodes["creative-form"].trigger("submit");
  assert.match(nodes["creative-status"].textContent,/Describe your design goal/);
  assert.doesNotMatch(nodes["creative-plan"].textContent,/Review plan/);
  nodes["creative-subject"].value="X".repeat(601);
  nodes["creative-form"].trigger("submit");
  assert.match(nodes["creative-status"].textContent,/600 characters/);
});
test("filters source library and shows accessible no-match message",()=>{
  const {nodes}=setup();
  nodes["creative-source-search"].value="GSAP";
  nodes["creative-source-search"].trigger("input");
  assert.equal(nodes["creative-sources"].children.length,1);
  nodes["creative-source-search"].value="does-not-exist";
  nodes["creative-source-search"].trigger("input");
  assert.match(nodes["creative-source-count"].textContent,/0 of 12 references/);
  assert.match(nodes["creative-sources"].textContent,/No matching sources/);
});
test("tampering with the source, skill execution or paid provider fails closed",()=>{
  for(const modify of [
    c=>{c.skills[0].executionEnabled=true;},
    c=>{c.sources[0].repository="attacker/malicious";},
    c=>{c.paidProvidersEnabled=true;}
  ]){
    const {nodes}=setup(modify);
    assert.equal(nodes["creative-subject"].disabled,true);
    assert.match(nodes["creative-status"].textContent,/integrity check failed/);
    assert.equal(nodes["creative-skill-list"].children.length,0);
  }
});
