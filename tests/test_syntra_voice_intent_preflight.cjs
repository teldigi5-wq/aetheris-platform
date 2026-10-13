"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const voice=require("../apps/syntra-desktop/voice/voice-intent-preflight.cjs");

const authorized={source:"TRUSTED_LOCAL_TRANSCRIPT",microphoneConsent:true,wakeModeEnabled:true};
function plan(text,ctx=authorized){return voice.planTranscript(text,ctx);}

test("all desired Syntra and Sintra wake aliases recognize only at the start",()=>{
  for(const w of ["hi sintra","Hi Syntra","hey sintra","Hey Syntra","syntra","Sintra"]){
    const r=plan(w);
    assert.equal(r.kind,"WAKE_ACK",w);
    assert.equal(r.executable,false);
    assert.equal(r.hardwareStatus,"BLOCKED_PENDING_HARDWARE");
  }
  for(const phrase of ["assistant syntra open notepad","i said hi sintra","asyntra open notepad",
     "inside sintra open notepad","listen to the word syntra","hi sintrable open notepad"]){
    assert.equal(plan(phrase).status,"REJECTED",phrase);
  }
});
test("wake mode and trusted local microphone consent are mandatory",()=>{
  for(const ctx of [{}, {source:"BROWSER_MIC",microphoneConsent:true,wakeModeEnabled:true},
    {source:"TRUSTED_LOCAL_TRANSCRIPT",microphoneConsent:false,wakeModeEnabled:true},
    {source:"TRUSTED_LOCAL_TRANSCRIPT",microphoneConsent:true,wakeModeEnabled:false}]){
    const r=plan("Hi Sintra, open notepad",ctx);
    assert.equal(r.status,"REJECTED");
  }
});
test("application launching requires exact allowlist and remains a proposal",()=>{
  const examples=[
    ["Hi Sintra, open calculator","calculator"],
    ["syntra launch VS Code","vscode"],
    ["hey syntra open file explorer","file-explorer"],
    ["sintra, start notepad","notepad"]
  ];
  for(const [spoken,app] of examples){
    const r=plan(spoken);
    assert.equal(r.kind,"APPLICATION_LAUNCH_REQUEST",spoken);
    assert.equal(r.approvedApp,app);
    assert.equal(r.approval,"OWNER_SCOPED_GRANT_REQUIRED");
    assert.equal(r.sandbox,"STAGED_TEST_BEFORE_HOST");
    assert.equal(r.executable,false);
  }
  for(const phrase of ["hi sintra open powershell","hi syntra open cmd.exe","syntra open ../../secrets",
                       "hi sintra open https://example.com","syntra open calculator && del /q c:\\*"]){
    const r=plan(phrase);
    assert.equal(r.kind,"UNSUPPORTED_APPLICATION",phrase);
    assert.equal(r.executable,false);
  }
});
test("spoken CEO, secretary and companion roles never create real authority",()=>{
  for(const [name,expected] of [["be my secretary","SECRETARY"],
    ["switch to ceo mode","CEO_ADVISER"],["be my companion","COMPANION"],
    ["act as tutor","TUTOR"],["be the chief of staff","CHIEF_OF_STAFF"]]){
    const r=plan("hi sintra "+name);
    assert.equal(r.kind,"ROLE_PROFILE_REQUEST");
    assert.equal(r.profile,expected);
    assert.equal(r.authorityUnchanged,true);
    assert.equal(r.executable,false);
  }
});
test("emergency spoken control has priority but no fake execution",()=>{
  for(const word of ["stop","emergency stop","stop everything","cancel all tasks"]){
    const r=plan("Syntra, "+word);
    assert.equal(r.kind,"CONTROL_REQUEST");
    assert.equal(r.control,"STOP");
    assert.equal(r.priority,"EMERGENCY");
    assert.equal(r.executable,false);
  }
  assert.equal(plan("Hi Sintra pause").control,"PAUSE");
  assert.equal(plan("Hi Sintra resume").control,"RESUME");
});
test("irreversible actions require approvals and sandbox",()=>{
  for(const spoken of ["hi sintra send an email",
    "syntra deploy the site","syntra delete my folder",
    "hey syntra buy 100 servers","sintra install my application",
    "sintra execute a command"]){
    const r=plan(spoken);
    assert.equal(r.kind,"HIGH_IMPACT_TASK_PROPOSAL",spoken);
    assert.equal(r.approval,"EXPLICIT_OWNER_APPROVAL");
    assert.equal(r.sandbox,"ISOLATED_DRY_RUN_REQUIRED");
    assert.equal(r.executable,false);
  }
});
test("learning does not silently persist, executive tasks do not claim access",()=>{
  const memory=plan("Hi Sintra remember my choice");
  assert.equal(memory.kind,"MEMORY_PROPOSAL");
  assert.equal(memory.retention,"NO_PERSISTENCE_UNTIL_REVIEW");
  const executive=plan("sintra prepare our company briefing");
  assert.equal(executive.kind,"EXECUTIVE_TASK_PROPOSAL");
  assert.equal(executive.sourceVerificationRequired,true);
});
test("invalid text, null, empty, control codes and huge transcripts are rejected",()=>{
  for(const input of [null,12,{}, "", "hi syntra\nopen notepad","hi syntra "+"x".repeat(2000),
      "hi syntra "+"a ".repeat(140)]){
    const r=plan(input);
    assert.equal(r.status,"REJECTED");
  }
});
test("voice route is pure-data and never executes a tool, network or operating-system call",()=>{
  const source=require("node:fs").readFileSync(require("node:path").resolve(__dirname,
    "../apps/syntra-desktop/voice/voice-intent-preflight.cjs"),"utf8");
  for(const forbidden of ["fetch(","XMLHttpRequest","WebSocket","child_process","spawn(","exec(","localStorage",
    "sessionStorage","__TAURI__","eval(","innerHTML","process.env","accessToken"]){
    assert.equal(source.includes(forbidden),false,forbidden);
  }
  for(const output of [plan("syntra"),plan("hi sintra open calculator"),
    plan("hi sintra delete data"),plan("hi sintra tell me a joke")]){
    assert.ok(Object.isFrozen(output));
    assert.equal(output.networkAttempted,false);
    assert.equal(output.executable,false);
  }
});
