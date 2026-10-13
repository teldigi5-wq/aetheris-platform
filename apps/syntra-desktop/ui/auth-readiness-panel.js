/* Native-only, read-only session readiness. No credentials or network requests. */
(() => {
  "use strict";
  const button=document.getElementById("check-auth-readiness");
  const display=document.getElementById("auth-readiness-value");
  if(!button||!display)return;

  button.addEventListener("click",async()=>{
    if(button.disabled)return;
    button.disabled=true;
    display.textContent="Checking native boundary…";
    try{
      const invoke=window.__TAURI__?.core?.invoke;
      if(typeof invoke!=="function"){
        display.textContent="Native shell unavailable. Authentication stays disabled.";
        return;
      }
      const result=await invoke("syntra_auth_readiness");
      if(!result||result.status!=="BLOCKED_NATIVE_CREDENTIAL_BOUNDARY"
        ||result.physicalPcStatus!=="BLOCKED_PENDING_HARDWARE"
        ||result.credentialVaultConnected!==false
        ||result.authenticatedSessionActive!==false
        ||result.canExecute!==false
        ||result.proposedReadOperations!==4){
        display.textContent="Unexpected native response. Authentication remains blocked.";
        return;
      }
      display.textContent="OS-backed credential vault not connected. All 4 proposed read routes remain disabled until native authentication and hardware verification.";
    }catch(_){
      // Errors are intentionally not interpolated: they might reveal local paths or other details.
      display.textContent="Native readiness check failed. Authentication remains blocked.";
    }finally{
      button.disabled=false;
    }
  });
})();
