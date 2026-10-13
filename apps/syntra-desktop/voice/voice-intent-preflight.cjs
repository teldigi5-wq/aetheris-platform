"use strict";

/*
 * Deterministic transcript-to-intent preflight. This module accepts TEXT ONLY.
 * No microphone, wake engine, app launcher, OS permission, model or network access.
 * All returned intents are non-executable proposals for later policy evaluation.
 */
const LIMIT=1024;
const INVOCATIONS=Object.freeze(["hey syntra","hi syntra","hey sintra","hi sintra","syntra","sintra"]);
const APPLICATIONS=Object.freeze({
  "calculator":"calculator",
  "notepad":"notepad",
  "file explorer":"file-explorer",
  "explorer":"file-explorer",
  "visual studio code":"vscode",
  "vs code":"vscode"
});
const ROLES=Object.freeze({
  "companion":"COMPANION",
  "secretary":"SECRETARY",
  "executive assistant":"SECRETARY",
  "chief of staff":"CHIEF_OF_STAFF",
  "ceo":"CEO_ADVISER",
  "ceo adviser":"CEO_ADVISER",
  "tutor":"TUTOR",
  "developer":"ENGINEER"
});
const BLOCKED="BLOCKED_PENDING_HARDWARE";
const MAX_WORDS=120;

function reject(reason){
  return Object.freeze({status:"REJECTED",kind:"NO_ACTION",reason,
    executable:false,networkAttempted:false});
}
function result(kind,extra={}){
  return Object.freeze({status:"PROPOSAL_ONLY",kind,executable:false,
    networkAttempted:false,hardwareStatus:BLOCKED,...extra});
}
function clean(text){
  if(typeof text!=="string"||text.length===0||text.length>LIMIT)return null;
  const normalized=text.normalize("NFKC").toLowerCase().trim();
  if(/[\u0000-\u001f\u007f]/.test(normalized))return null;
  // Discard lightweight speech punctuation only; NEVER turn a URL, path,
  // quoted instruction or shell command into an OS execution request.
  const plain=normalized.replace(/^[\s,.!?;:]+|[\s,.!?;:]+$/g,"")
    .replace(/\s+/g," ");
  return plain&&plain.split(" ").length<=MAX_WORDS?plain:null;
}
function splitWake(text){
  for(const name of INVOCATIONS){
    if(text===name)return {wake:name,request:""};
    if(text.startsWith(name+" ")||text.startsWith(name+",")||text.startsWith(name+".")||text.startsWith(name+":")){
      const remaining=text.slice(name.length).replace(/^[\s,.:;!?]+/,"").trim();
      return {wake:name,request:remaining};
    }
  }
  return null;
}
function planTranscript(transcript,context={}){
  const body=clean(transcript);
  if(body===null)return reject("INVALID_OR_OVERSIZED_TRANSCRIPT");
  // Trust originates in a future local capture adapter, not the text itself.
  // Passing this guard does NOT authorize host execution or verify a speaker.
  if(context.source!=="TRUSTED_LOCAL_TRANSCRIPT"||context.microphoneConsent!==true
    ||context.wakeModeEnabled!==true)return reject("VOICE_INPUT_NOT_AUTHORIZED");
  const wake=splitWake(body);
  if(!wake)return reject("WAKE_PHRASE_NOT_AT_START");
  const command=wake.request.replace(/^[\s,.:;!?]+|[\s,.:;!?]+$/g,"").trim();
  if(!command)return result("WAKE_ACK",{phrase:wake.wake,responseHint:"Listening. What would you like me to help with?"});
  const common={phrase:wake.wake};
  if(/^(stop|emergency stop|stop everything|cancel all tasks|pause all tasks|pause|resume|continue)$/.test(command)){
    const control=command.includes("stop")||command.includes("cancel")?"STOP":
      command.startsWith("pause")?"PAUSE":"RESUME";
    return result("CONTROL_REQUEST",{...common,control,priority:control==="STOP"?"EMERGENCY":"HIGH",
      route:"EXISTING_GOVERNED_CONTROL_PLANE",requiresTrustedControlChannel:true});
  }
  const open=command.match(/^(?:please )?(?:open|launch|start) (?:the )?(.+)$/);
  if(open){
    const requested=open[1].trim().replace(/[.!?]$/,"");
    if(!Object.prototype.hasOwnProperty.call(APPLICATIONS,requested))
      return result("UNSUPPORTED_APPLICATION",{...common,reason:"APP_NOT_ALLOWLISTED",
        requestedLabel:requested.slice(0,80),approvedApp:null});
    return result("APPLICATION_LAUNCH_REQUEST",{...common,approvedApp:APPLICATIONS[requested],
      capability:"app.launch."+APPLICATIONS[requested],risk:"L2_LOCAL_EXECUTION",
      sandbox:"STAGED_TEST_BEFORE_HOST",approval:"OWNER_SCOPED_GRANT_REQUIRED"});
  }
  const role=command.match(/^(?:switch to|act as|be my|be the) (.+?)(?: mode)?$/);
  if(role){
    const key=role[1].trim();
    if(Object.prototype.hasOwnProperty.call(ROLES,key))
      return result("ROLE_PROFILE_REQUEST",{...common,profile:ROLES[key],
        authorityUnchanged:true,requiresModelForReply:true});
  }
  if(/^(?:remember|save to memory|learn this)\b/.test(command))
    return result("MEMORY_PROPOSAL",{...common,requiresApproval:true,
      retention:"NO_PERSISTENCE_UNTIL_REVIEW",memoryScope:"OWNER_REVIEW_REQUIRED"});
  if(/\b(?:send|publish|deploy|purchase|buy|pay|transfer|delete|erase|wipe|install|uninstall|run command|terminal|execute)\b/.test(command))
    return result("HIGH_IMPACT_TASK_PROPOSAL",{...common,approval:"EXPLICIT_OWNER_APPROVAL",
      sandbox:"ISOLATED_DRY_RUN_REQUIRED",risk:"L3_OR_L4_REVIEW",requiresModelForPlan:true});
  if(/\b(?:briefing|schedule|calendar|meeting|inbox|company|ceo|executive|secretary|business)\b/.test(command))
    return result("EXECUTIVE_TASK_PROPOSAL",{...common,approval:"POLICY_DEPENDENT",
      requiresModelForPlan:true,sourceVerificationRequired:true});
  return result("CONVERSATION_OR_TASK_PROPOSAL",{...common,requiresModelForReply:true,
    approval:"POLICY_DEPENDENT",requiresOwnerContext:true});
}
module.exports=Object.freeze({LIMIT,INVOCATIONS,APPLICATIONS,ROLES,clean,splitWake,planTranscript});
