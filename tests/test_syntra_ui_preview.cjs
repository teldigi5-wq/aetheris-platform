"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const {test}=require("node:test");
const ui=path.resolve(__dirname,"../apps/syntra-desktop/ui");
class MockNode {
  constructor(tag="div"){
    this.tagName=tag;this.value="";this.disabled=false;this.children=[];this.events={};
    this._text="";this.className="";this.attributes={};this.selected=false;this.focused=false;
    this.styles={};
    this.style={setProperty:(key,value)=>{this.styles[key]=value;}};
  }
  get firstChild(){return this.children[0]||null;}
  set textContent(v){this._text=String(v);this.children=[];}
  get textContent(){return this._text+this.children.map(x=>x.textContent).join("");}
  append(...nodes){this.children.push(...nodes);}
  removeChild(node){const index=this.children.indexOf(node);if(index>=0)this.children.splice(index,1);}
  addEventListener(kind,fn){this.events[kind]=fn;}
  setAttribute(key,value){this.attributes[key]=String(value);}
  focus(){this.focused=true;}
  select(){this.selected=true;}
  trigger(kind){assert.equal(typeof this.events[kind],"function");return this.events[kind]();}
}
const names=["spec-eyebrow","spec-headline","spec-description","spec-action","spec-palette","spec-layout",
"spec-preview","spec-export","spec-status","spec-copy","spec-reset","spec-device-desktop","spec-device-mobile",
"spec-quality-status","spec-quality-list","spec-quality-warnings"];
function setup(withClipboard=true){
  const els=Object.fromEntries(names.map(id=>[id,new MockNode(id)]));
  const clipboard={calls:[],async writeText(s){this.calls.push(s);}};
  const ctx={
    window:{},navigator:withClipboard?{clipboard}:{},
    document:{getElementById:id=>els[id],createElement:tag=>new MockNode(tag)},
    module:undefined
  };
  vm.runInNewContext(fs.readFileSync(path.join(ui,"creative-ui-spec-engine.js"),"utf8"),ctx);
  vm.runInNewContext(fs.readFileSync(path.join(ui,"design-quality-gates.js"),"utf8"),ctx);
  vm.runInNewContext(fs.readFileSync(path.join(ui,"creative-ui-preview.js"),"utf8"),ctx);
  return {els,clipboard,ctx};
}
test("loads default local preview, desktop mode, and one-file source",()=>{
  const {els}=setup();
  assert.match(els["spec-preview"].textContent,/Build something worth remembering/);
  assert.match(els["spec-preview"].textContent,/DESIGNED WITH INTENTION/);
  assert.match(els["spec-preview"].className,/spec-device-desktop/);
  assert.match(els["spec-export"].value,/<!doctype html>/);
  assert.match(els["spec-export"].value,/default-src 'none'/);
  assert.equal(els["spec-copy"].disabled,false);
  assert.match(els["spec-quality-status"].textContent,/7\/7 contrast pair checks passed/);
  assert.equal(els["spec-quality-list"].children.length,7);
});
test("live changes affect DOM preview and exported HTML with no interpretation",()=>{
  const {els}=setup();
  const headline='<img src=x onerror=alert(1)> Luxury';
  els["spec-headline"].value=headline;
  els["spec-headline"].trigger("input");
  assert.match(els["spec-preview"].textContent,/<img src=x onerror=alert\(1\)> Luxury/);
  assert.ok(els["spec-export"].value.includes("&lt;img src=x onerror=alert(1)&gt; Luxury"));
  assert.doesNotMatch(els["spec-export"].value,/<img\b/i);
});
test("preset and device switches produce different layouts without plugins",()=>{
  const {els}=setup();
  els["spec-palette"].value="ivory";
  els["spec-palette"].trigger("change");
  assert.match(els["spec-preview"].className,/spec-ivory/);
  assert.equal(els["spec-preview"].styles["--spec-background"],"#f9f6ef");
  els["spec-layout"].value="centered";
  els["spec-layout"].trigger("change");
  assert.match(els["spec-preview"].className,/spec-centered/);
  els["spec-device-mobile"].trigger("click");
  assert.match(els["spec-preview"].className,/spec-device-mobile/);
  assert.equal(els["spec-device-mobile"].attributes["aria-pressed"],"true");
  assert.equal(els["spec-device-desktop"].attributes["aria-pressed"],"false");
});
test("invalid values fail closed: no stale code available to copy",()=>{
  const {els}=setup();
  els["spec-action"].value=" ";
  els["spec-action"].trigger("input");
  assert.equal(els["spec-export"].value,"");
  assert.equal(els["spec-copy"].disabled,true);
  assert.match(els["spec-status"].textContent,/invalid fields/i);
  els["spec-action"].value="Review design";
  els["spec-action"].trigger("input");
  assert.match(els["spec-export"].value,/Review design/);
  assert.equal(els["spec-copy"].disabled,false);
});
test("only user gesture copies compiled HTML and reset restores values",async()=>{
  const {els,clipboard}=setup();
  assert.equal(clipboard.calls.length,0);
  els["spec-headline"].value="A custom product story";
  els["spec-headline"].trigger("input");
  await els["spec-copy"].trigger("click");
  assert.equal(clipboard.calls.length,1);
  assert.match(clipboard.calls[0],/A custom product story/);
  els["spec-reset"].trigger("click");
  assert.equal(els["spec-headline"].value,"Build something worth remembering.");
  assert.match(els["spec-status"].textContent,/safe preset defaults/);
});
test("unavailable clipboard selects code for manual copy without external requests",async()=>{
  const {els}=setup(false);
  await els["spec-copy"].trigger("click");
  assert.equal(els["spec-export"].focused,true);
  assert.equal(els["spec-export"].selected,true);
  assert.match(els["spec-status"].textContent,/manual copy/);
});
