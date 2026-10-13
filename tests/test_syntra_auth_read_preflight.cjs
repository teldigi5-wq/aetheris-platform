"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const planner=require("../apps/syntra-desktop/auth-read-preflight.cjs");
const contract=JSON.parse(fs.readFileSync(path.join(__dirname,"../apps/syntra-desktop/authenticated-api.contract.json"),"utf8"));
const fresh=()=>structuredClone(contract);

test("source contract offers only four known read-only request plans",()=>{
  assert.equal(planner.verifyContract(contract),true);
  const all=planner.readinessMatrix(contract);
  assert.equal(all.length,4);
  assert.ok(Object.isFrozen(all));
  assert.deepEqual(Object.keys(planner.OPERATIONS).sort(),["emergency-stop-state","local-model-health","pending-approvals","task-list"]);
  for(const item of all){
    assert.equal(item.method,"GET");
    assert.equal(item.scope,"orchestrator:read");
    assert.equal(item.networkAttempted,false);
    assert.equal(item.canExecute,false);
    assert.equal(item.bearerRequired,true);
    assert.equal(item.status,"BLOCKED_NATIVE_CREDENTIAL_BOUNDARY");
    assert.match(item.path,/^\/api\/orchestrator\/[-/a-z]+$/);
    assert.ok(Object.isFrozen(item));
    assert.equal(Object.hasOwn(item,"token"),false);
    assert.equal(Object.hasOwn(item,"headers"),false);
    assert.equal(Object.hasOwn(item,"url"),false);
  }
});

test("reject mutation routes, memory ownership, caller-supplied URLs and injection names",()=>{
  for(const operation of [
    "create-task","approve","cancel","memory","POST /api/orchestrator/tasks",
    "/api/orchestrator/models/execute","http://127.0.0.1:8080",
    "/api/orchestrator/memory/capabilities","__proto__","constructor",
    "task-list?malicious=true",null,{},["task-list"]
  ]){
    assert.throws(()=>planner.planRead(contract,operation),/preflight rejected/);
  }
});

test("fail closed on source contract drift, elevated permissions and authority changes",()=>{
  const mutations=[
    c=>{c.contractStatus="LIVE_WIRED";},
    c=>{c.contractVersion="2.0.0";},
    c=>{c.gateway.loopbackDevelopmentBase="http://attacker.invalid";},
    c=>{c.gateway.orchestratorPath="/api/**";},
    c=>{c.gateway.authentication.scheme="Basic";},
    c=>{c.gateway.authentication.header="X-Bearer";},
    c=>{c.gateway.authentication.requiredReadScope="orchestrator:write";},
    c=>{c.gateway.authentication.requiredWriteScope="admin:*";},
    c=>{c.gateway.authentication.missingBearerStatus=200;},
    c=>{c.gateway.authentication.invalidOrExpiredStatus=200;},
    c=>{c.gateway.authentication.missingScopeStatus=200;},
    c=>{c.gateway.clientSuppliedIdentityHeadersAreReplaced=false;},
    c=>{c.identity.desktopSecretStorage="localStorage";},
    c=>{c.desktopBoundary.backendWiringAllowed=true;},
    c=>{c.desktopBoundary.productionActivationAllowed=true;},
    c=>{c.desktopBoundary.physicalPcStatus="READY";},
    c=>{c.desktopBoundary.currentDirectRuntimeOperation="EXECUTE_TASKS";},
    c=>{c.endpoints.tasks[1].method="POST";},
    c=>{c.endpoints.tasks[1].scope="orchestrator:write";},
    c=>{c.endpoints.tasks[1].wired=true;},
    c=>{c.endpoints.tasks[1].path="/api/orchestrator/tasks/../../secrets";},
    c=>{c.endpoints.tasks.push({...c.endpoints.tasks[1]});},
    c=>{c.endpoints.localModel=[];},
    c=>{c.endpoints.approvals=null;}
  ];
  for(const mutate of mutations){
    const c=fresh();mutate(c);
    assert.throws(()=>planner.readinessMatrix(c),/preflight rejected/);
  }
});

test("only static metadata returned; no network or secret-handling dependencies",()=>{
  const source=fs.readFileSync(path.join(__dirname,"../apps/syntra-desktop/auth-read-preflight.cjs"),"utf8");
  for(const forbidden of ["fetch(","XMLHttpRequest","WebSocket(","http.request(","https.request(","localStorage","sessionStorage","__TAURI__","eval(","Authorization: Bearer ","child_process","exec(","writeFile("]){
    assert.equal(source.includes(forbidden),false,forbidden);
  }
  const frozen=planner.planRead(contract,"pending-approvals");
  assert.equal(frozen.path,"/api/orchestrator/approvals/pending");
  assert.equal(JSON.stringify(frozen).includes("accessToken"),false);
  assert.equal(JSON.stringify(frozen).includes("refreshToken"),false);
});
