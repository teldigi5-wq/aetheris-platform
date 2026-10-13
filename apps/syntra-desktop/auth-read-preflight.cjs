"use strict";

/**
 * Syntra authenticated API read-route preflight only.
 * This does not make HTTP requests, handle bearer tokens, or enable Tauri IPC.
 * Real authenticated sessions require separately certified native credential storage.
 */
const OPERATIONS=Object.freeze({
  "task-list":Object.freeze({group:"tasks",path:"/api/orchestrator/tasks",purpose:"Read task summaries"}),
  "pending-approvals":Object.freeze({group:"approvals",path:"/api/orchestrator/approvals/pending",purpose:"Read pending approval summaries"}),
  "local-model-health":Object.freeze({group:"localModel",path:"/api/orchestrator/models/local/health",purpose:"Read local model health"}),
  "emergency-stop-state":Object.freeze({group:"controls",path:"/api/orchestrator/control/emergency-stop",purpose:"Read emergency stop state"})
});
const READ_SCOPE="orchestrator:read";
const REQUIRED_STATUS="SOURCE_VERIFIED_NOT_WIRED";
const BLOCKED_STATUS="BLOCKED_NATIVE_CREDENTIAL_BOUNDARY";

function fail(message){throw new TypeError("Authenticated API preflight rejected: "+message);}
function assertObject(obj,reason){
  if(!obj||typeof obj!=="object"||Array.isArray(obj))fail(reason);
}
function verifyContract(contract){
  assertObject(contract,"missing contract");
  if(contract.contractVersion!=="1.0.0"||contract.contractStatus!==REQUIRED_STATUS)
    fail("unsupported or activated API contract");
  assertObject(contract.gateway,"missing gateway");
  assertObject(contract.gateway.authentication,"missing authentication contract");
  if(contract.gateway.loopbackDevelopmentBase!=="http://127.0.0.1:8080"
    ||contract.gateway.orchestratorPath!=="/api/orchestrator/**"
    ||contract.gateway.authentication.scheme!=="Bearer"
    ||contract.gateway.authentication.header!=="Authorization"
    ||contract.gateway.authentication.requiredReadScope!==READ_SCOPE
    ||contract.gateway.authentication.requiredWriteScope!=="orchestrator:write"
    ||contract.gateway.authentication.missingBearerStatus!==401
    ||contract.gateway.authentication.invalidOrExpiredStatus!==401
    ||contract.gateway.authentication.missingScopeStatus!==403
    ||contract.gateway.clientSuppliedIdentityHeadersAreReplaced!==true)
    fail("gateway authentication boundary drift");
  assertObject(contract.identity,"missing identity");
  if(contract.identity.desktopSecretStorage!=="OS_BACKED_REQUIRED_BEFORE_SESSION_WIRING")
    fail("missing OS-secret-storage requirement");
  assertObject(contract.desktopBoundary,"missing desktop authority boundary");
  if(contract.desktopBoundary.backendWiringAllowed!==false
    ||contract.desktopBoundary.productionActivationAllowed!==false
    ||contract.desktopBoundary.currentDirectRuntimeOperation!=="GET_LOOPBACK_ACTUATOR_HEALTH_ONLY"
    ||contract.desktopBoundary.physicalPcStatus!=="BLOCKED_PENDING_HARDWARE")
    fail("runtime authority or hardware truth boundary drift");
  assertObject(contract.endpoints,"missing endpoint contract");
  for(const entry of Object.values(OPERATIONS)){
    const collection=contract.endpoints[entry.group];
    if(!Array.isArray(collection))fail("missing endpoint group");
    const matches=collection.filter(item=>item&&item.path===entry.path);
    if(matches.length!==1||matches[0].method!=="GET"
      ||matches[0].scope!==READ_SCOPE||matches[0].wired!==false)
      fail("unsafe or missing read endpoint: "+entry.path);
  }
  return true;
}

function planRead(contract,operation){
  verifyContract(contract);
  if(typeof operation!=="string"||!Object.prototype.hasOwnProperty.call(OPERATIONS,operation))
    fail("unknown or unsafe operation");
  const entry=OPERATIONS[operation];
  // Never build a live URL or Authorization header: this is a review-only request descriptor.
  return Object.freeze({
    operation,purpose:entry.purpose,method:"GET",path:entry.path,scope:READ_SCOPE,
    bearerRequired:true,networkAttempted:false,canExecute:false,
    status:BLOCKED_STATUS,
    reason:"Source-verified route only; OS-backed native authentication, signed claims, owner-PC validation, and explicit approval remain outstanding."
  });
}
function readinessMatrix(contract){
  verifyContract(contract);
  return Object.freeze(Object.keys(OPERATIONS).map(key=>planRead(contract,key)));
}
module.exports=Object.freeze({OPERATIONS,READ_SCOPE,REQUIRED_STATUS,BLOCKED_STATUS,verifyContract,planRead,readinessMatrix});
