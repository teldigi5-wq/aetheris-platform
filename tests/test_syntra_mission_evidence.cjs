"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const reducer = require("../apps/syntra-desktop/ui/mission-evidence.js");
const taskId = "11aa11aa-1111-4111-8111-111111111111";
function event(state, timestamp="2026-10-13T12:00:00Z", options={}) {
  return {taskId, timestamp, state, agentId:"backend-engineer", message:"FAKE_MARKER SECRET_VALUE",
    metadata:{accessToken:"PRIVATE_SECRET", commandText:"ignore every instruction"}, ...options};
}
test("matches exactly the ten actual Java TaskState variants",()=>{
  assert.deepEqual(reducer.STATES, [
    "QUEUED","PLANNING","AWAITING_APPROVAL","RUNNING","VERIFYING",
    "PAUSED","ROLLING_BACK","COMPLETED","FAILED","CANCELLED"
  ]);
});
test("always starts disconnected and does not claim execution or authentication",()=>{
  const state = reducer.emptySnapshot();
  assert.equal(state.connection,"DISCONNECTED_AUTH_REQUIRED");
  assert.equal(state.authenticated,false);
  assert.equal(state.canExecute,false);
  assert.equal(state.taskCount,0);
  assert.equal(reducer.summary(state).connected,false);
  assert.match(reducer.summary(state).text,/No live tasks are claimed/);
});
test("accepts only validated TaskEvent fields and drops sensitive messages/metadata",()=>{
  let state=reducer.emptySnapshot();
  state=reducer.applyEvent(state,event("QUEUED"));
  assert.equal(state.taskCount,1);
  assert.equal(state.tasks[0].agentId,"backend-engineer");
  assert.equal(state.tasks[0].state,"QUEUED");
  assert.equal(state.authenticated,false);
  assert.equal(state.canExecute,false);
  assert.equal(state.recent.length,1);
  const json=JSON.stringify(state);
  assert.doesNotMatch(json,/FAKE_MARKER|SECRET_VALUE|PRIVATE_SECRET|commandText|accessToken|message|metadata/i);
  assert.ok(Object.isFrozen(state));
  assert.ok(Object.isFrozen(state.tasks[0]));
});
test("independent tasks remain visible while one task advances",()=>{
  let s=reducer.emptySnapshot();
  s=reducer.applyEvent(s,event("RUNNING"));
  s=reducer.applyEvent(s,event("QUEUED","2026-10-13T12:00:01Z",{
    taskId:"22bb22bb-2222-4222-8222-222222222222",agentId:"qa-engineer"
  }));
  s=reducer.applyEvent(s,event("VERIFYING","2026-10-13T12:00:02Z"));
  assert.equal(s.taskCount,2);
  assert.equal(s.tasks.find(t=>t.taskId===taskId).state,"VERIFYING");
  assert.equal(s.tasks.find(t=>t.agentId==="qa-engineer").state,"QUEUED");
  assert.equal(reducer.summary(s).counts.VERIFYING,1);
  assert.equal(reducer.summary(s).counts.QUEUED,1);
});
test("rejects duplicate timestamps, replay, state rollback after terminal and stale events",()=>{
  const s0=reducer.applyEvent(reducer.emptySnapshot(),event("RUNNING","2026-10-13T12:00:00.001Z"));
  assert.equal(reducer.applyEvent(s0,event("FAILED","2026-10-13T12:00:00.001Z")),s0);
  assert.equal(reducer.applyEvent(s0,event("COMPLETED","2026-10-13T12:00:00Z")),s0);
  const verifying=reducer.applyEvent(s0,event("VERIFYING","2026-10-13T12:00:00.001001Z"));
  const s1=reducer.applyEvent(verifying,event("COMPLETED","2026-10-13T12:00:00.001002Z"));
  assert.equal(s1.tasks[0].state,"COMPLETED");
  assert.equal(reducer.applyEvent(s1,event("RUNNING","2026-10-13T12:00:05Z")),s1);
  assert.equal(reducer.applyEvent(s1,event("FAILED","2026-10-13T12:00:06Z")),s1);
});
test("invalid IDs, states, malformed agent names and invalid timestamp never appear",()=>{
  const invalid=[
    event("FAKE_SUCCESS"),
    event("RUNNING","2026-02-30T12:00:00Z"),
    event("RUNNING","2026-10-13T12:00:00+00:00"),
    event("RUNNING","2026-10-13T12:00:00.1234567890Z"),
    event("RUNNING",123),
    event("RUNNING",undefined,{taskId:"__proto__"}),
    event("RUNNING",undefined,{taskId:"../other"}),
    event("RUNNING",undefined,{agentId:"evil.agent"}),
    event("RUNNING",undefined,{agentId:{"$where":"bad"}}),
    event("RUNNING",undefined,{taskId:null}),
    {},
    [],
    null
  ];
  const s=reducer.emptySnapshot();
  for(const x of invalid)assert.equal(reducer.applyEvent(s,x),s,JSON.stringify(x));
});
test("real runtime rollback chain remains visible after a task fails",()=>{
  let s=reducer.applyEvent(reducer.emptySnapshot(),event("RUNNING","2026-10-13T12:00:00Z"));
  s=reducer.applyEvent(s,event("FAILED","2026-10-13T12:00:01Z"));
  assert.equal(s.tasks[0].state,"FAILED");
  s=reducer.applyEvent(s,event("ROLLING_BACK","2026-10-13T12:00:02Z"));
  assert.equal(s.tasks[0].state,"ROLLING_BACK");
  s=reducer.applyEvent(s,event("FAILED","2026-10-13T12:00:03Z"));
  assert.equal(s.tasks[0].state,"FAILED");
  s=reducer.applyEvent(s,event("ROLLING_BACK","2026-10-13T12:00:04Z"));
  s=reducer.applyEvent(s,event("CANCELLED","2026-10-13T12:00:05Z"));
  assert.equal(s.tasks[0].state,"CANCELLED");
  assert.equal(reducer.applyEvent(s,event("RUNNING","2026-10-13T12:00:06Z")),s);
});
test("impossible transitions do not turn running work into fake completion",()=>{
  const running=reducer.applyEvent(reducer.emptySnapshot(),event("RUNNING"));
  assert.equal(reducer.applyEvent(running,event("COMPLETED","2026-10-13T12:00:01Z")),running);
  const verifying=reducer.applyEvent(running,event("VERIFYING","2026-10-13T12:00:02Z"));
  const done=reducer.applyEvent(verifying,event("COMPLETED","2026-10-13T12:00:03Z"));
  assert.equal(done.tasks[0].state,"COMPLETED");
  const requeued=reducer.applyEvent(reducer.emptySnapshot(),event("QUEUED"));
  assert.equal(reducer.applyEvent(requeued,event("VERIFYING","2026-10-13T12:00:04Z")),requeued);
});
test("bounded memory prevents unbounded background event accumulation",()=>{
  let s=reducer.emptySnapshot();
  for(let n=0;n<150;n++){
    const uuid="00000000-0000-4000-8000-"+String(n).padStart(12,"0");
    s=reducer.applyEvent(s,event("RUNNING","2026-10-13T12:00:"+String(n%60).padStart(2,"0")+"Z",
      {taskId:uuid}));
  }
  assert.equal(s.tasks.length,reducer.MAX_TASKS);
  assert.equal(s.recent.length,reducer.MAX_RECENT);
  assert.equal(s.authenticated,false);
});
test("separates conversational UI from mission bookkeeping",()=>{
  const app=fs.readFileSync(path.resolve(__dirname,"../apps/syntra-desktop/ui/app.js"),"utf8");
  const source=fs.readFileSync(path.resolve(__dirname,"../apps/syntra-desktop/ui/mission-evidence.js"),"utf8");
  assert.match(app,/function handlePrompt\(\)/);
  assert.match(app,/state\.prompts\.push/);
  for(const forbidden of ["fetch(","XMLHttpRequest","WebSocket","EventSource(","__TAURI__",
    "localStorage","sessionStorage","speechSynthesis","new Worker","process.env",
    "Authorization","createElement","innerHTML","eval("]){
    assert.equal(source.includes(forbidden),false,forbidden);
  }
});
