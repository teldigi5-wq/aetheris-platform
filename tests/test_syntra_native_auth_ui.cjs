"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const source=fs.readFileSync(path.resolve(__dirname,"../apps/syntra-desktop/ui/auth-readiness-panel.js"),"utf8");
const blocked={
  status:"BLOCKED_NATIVE_CREDENTIAL_BOUNDARY",
  physicalPcStatus:"BLOCKED_PENDING_HARDWARE",
  credentialVaultConnected:false,
  authenticatedSessionActive:false,
  canExecute:false,
  proposedReadOperations:4
};
function setup(invoke){
  const button={disabled:false,events:{},addEventListener(name,fn){this.events[name]=fn;},click(){return this.events.click();}};
  const display={textContent:"Not checked"};
  const document={getElementById(id){return id==="check-auth-readiness"?button:id==="auth-readiness-value"?display:null;}};
  const window=invoke===undefined?{}:{__TAURI__:{core:{invoke}}};
  vm.runInNewContext(source,{document,window});
  return {button,display};
}
test("desktop shell not present fails closed without making requests",async()=>{
  const {button,display}=setup();
  await button.click();
  assert.match(display.textContent,/Native shell unavailable/);
  assert.equal(button.disabled,false);
});
test("valid blocked native report is truthful and never returns a credential",async()=>{
  const called=[];
  const {button,display}=setup(async(command)=>{called.push(command);return blocked;});
  await button.click();
  assert.deepEqual(called,["syntra_auth_readiness"]);
  assert.match(display.textContent,/credential vault not connected/i);
  assert.match(display.textContent,/remain disabled/i);
  assert.equal(button.disabled,false);
});
test("surprising or forged readiness responses never enable authenticated operations",async()=>{
  for(const fake of [
    null,{}, {...blocked,status:"READY"}, {...blocked,canExecute:true},
    {...blocked,credentialVaultConnected:true},
    {...blocked,authenticatedSessionActive:true},
    {...blocked,physicalPcStatus:"CERTIFIED"},
    {...blocked,proposedReadOperations:20}
  ]){
    const {button,display}=setup(async()=>fake);
    await button.click();
    assert.match(display.textContent,/Unexpected native response/);
    assert.equal(button.disabled,false);
  }
});
test("IPC error messages are not exposed to UI and reset disabled flag",async()=>{
  const {button,display}=setup(async()=>{throw Error("sensitive local path /private/fake");});
  await button.click();
  assert.match(display.textContent,/readiness check failed/);
  assert.doesNotMatch(display.textContent,/private|sensitive/i);
  assert.equal(button.disabled,false);
});
test("duplicate click while native request is pending does not invoke twice",async()=>{
  let resolve;
  let count=0;
  const pending=new Promise(r=>{resolve=r;});
  const {button,display}=setup(async()=>{count++;return pending;});
  const first=button.click();
  assert.equal(button.disabled,true);
  await button.click();
  assert.equal(count,1);
  resolve(blocked);
  await first;
  assert.match(display.textContent,/remain disabled/);
});
test("UI boundary contains no direct HTTP, tokens, local persistence or dynamic HTML",()=>{
  for(const forbidden of ["fetch(","XMLHttpRequest","WebSocket","EventSource","localStorage","sessionStorage","innerHTML","outerHTML","eval(","writeText(","accessToken","refreshToken","Authorization","https://"]){
    assert.equal(source.includes(forbidden),false,forbidden);
  }
});
