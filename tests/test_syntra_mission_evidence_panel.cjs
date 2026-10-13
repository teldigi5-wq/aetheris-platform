"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const reducer=require("../apps/syntra-desktop/ui/mission-evidence.js");
const source=fs.readFileSync(path.resolve(__dirname,"../apps/syntra-desktop/ui/mission-evidence-panel.js"),"utf8");
const taskId="11aa11aa-1111-4111-8111-111111111111";
function event(state="RUNNING",at="2026-10-13T12:00:00Z") {
  return {taskId,timestamp:at,state,agentId:"backend-engineer",
    message:"Sensitive content DO_NOT_SHOW",metadata:{accessToken:"DONT_DISPLAY"}};
}
function element(tag) {
  return {tag,textContent:"",children:[],files:[],listeners:{},append(...x){this.children.push(...x);},
    replaceChildren(...xs){this.children=[...xs];},
    addEventListener(t,fn){this.listeners[t]=fn;},
    async fire(t){return this.listeners[t]();}};
}
function harness(){
  const elems={
    "syntra-mission-evidence-file":element("input"),
    "syntra-mission-evidence-status":element("p"),
    "syntra-mission-evidence-summary":element("div"),
    "syntra-mission-evidence-list":element("div")
  };
  const document={
    getElementById(id){return elems[id]||null;},
    createElement(tag){return element(tag);}
  };
  const window={SyntraMissionEvidence:reducer};
  vm.runInNewContext(source,{document,window});
  const file=elems["syntra-mission-evidence-file"];
  const status=elems["syntra-mission-evidence-status"];
  const summary=elems["syntra-mission-evidence-summary"];
  const list=elems["syntra-mission-evidence-list"];
  const renderText=node=>String(node.textContent||"")+(node.children||[]).map(renderText).join(" ");
  return {file,status,summary,list,renderText,
    async load(value,{size=null,fail=false}={}){
      const json=typeof value==="string"?value:JSON.stringify(value);
      file.files=[{size:size===null?json.length:size,async text(){if(fail)throw Error("PRIVATE /token/path");return json;}}];
      await file.fire("change");
      return renderText(summary)+" "+renderText(list)+" "+status.textContent;
    }};
}
test("initial inspector has no verified connection and no fabricated tasks",()=>{
  const x=harness();
  const shown=x.renderText(x.summary)+" "+x.renderText(x.list);
  assert.match(shown,/Not connected/);
  assert.match(shown,/None/);
  assert.equal(x.list.children.length,0);
});
test("real task event format can be reviewed offline without revealing sensitive data",async()=>{
  const x=harness();
  const shown=await x.load([event("RUNNING"),event("VERIFYING","2026-10-13T12:00:01Z"),event("COMPLETED","2026-10-13T12:00:02Z")]);
  assert.match(shown,/OFFLINE IMPORT · UNVERIFIED/);
  assert.match(shown,/Completed \(imported\) 1/);
  assert.match(shown,/COMPLETED/);
  assert.match(shown,/backend-engineer/);
  assert.doesNotMatch(shown,/Sensitive content|DO_NOT_SHOW|DONT_DISPLAY|accessToken|metadata/i);
});
test("forged or stale events do not become completed work",async()=>{
  const x=harness();
  const shown=await x.load([
    event("RUNNING"),
    event("COMPLETED","2026-10-13T11:59:59Z"),
    {taskId:"../../evil",timestamp:"2026-10-13T12:00:05Z",state:"COMPLETED"}
  ]);
  assert.match(shown,/1 accepted event updates, 2 ignored/);
  assert.match(shown,/Running \(imported\) 1/);
  assert.match(shown,/Completed \(imported\) 0/);
});
test("invalid JSON, oversized files and invalid schemas fail closed",async()=>{
  const x=harness();
  for(const arg of ["{bad json", {anything:1}, "not an array"]){
    const msg=await x.load(arg);
    assert.match(msg,/Import (failed|rejected)/);
    assert.match(x.renderText(x.summary),/Not connected/);
    assert.equal(x.list.children.length,0);
  }
  const oversized=await x.load([event()],{size:256*1024+1});
  assert.match(oversized,/Import rejected/);
  const massive=await x.load(Array.from({length:501},()=>event()));
  assert.match(massive,/at most 500 task events/);
});
test("file API errors don't reveal local paths and don't persist parsed records",async()=>{
  const x=harness();
  const text=await x.load([event()],{fail:true});
  assert.match(text,/Import failed/);
  assert.doesNotMatch(text,/PRIVATE|token\/path/);
  assert.equal(x.list.children.length,0);
});
test("untrusted local input has no privileged command or network interface",()=>{
  for(const forbidden of ["fetch(","XMLHttpRequest","WebSocket","EventSource(",
    "__TAURI__","Authorization","localStorage","sessionStorage","innerHTML","outerHTML",
    "createObjectURL","eval(","process.env","console.log"]){
    assert.equal(source.includes(forbidden),false,forbidden);
  }
  assert.match(source,/input\.files/);
  assert.match(source,/file\.text\(/);
  assert.match(source,/OFFLINE IMPORT · UNVERIFIED/);
  const markup=fs.readFileSync(path.resolve(__dirname,"../apps/syntra-desktop/ui/index.html"),"utf8");
  for(const id of ["syntra-mission-evidence-file","syntra-mission-evidence-status","syntra-mission-evidence-list"]){
    assert.ok(markup.includes(id));
  }
  assert.ok(markup.indexOf('src="./mission-evidence.js"') <
            markup.indexOf('src="./mission-evidence-panel.js"'));
});
