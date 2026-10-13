/* Live, offline DOM preview for a bounded static HTML concept. No agents, tools or network. */
(() => {
  "use strict";
  const engine=window.SyntraUiSpecEngine;
  const quality=window.SyntraDesignQuality;
  const ids=["spec-eyebrow","spec-headline","spec-description","spec-action","spec-palette",
    "spec-layout","spec-preview","spec-export","spec-status","spec-copy",
    "spec-reset","spec-undo","spec-redo","spec-device-desktop","spec-device-mobile",
    "spec-quality-status","spec-quality-list","spec-quality-warnings",
    "spec-snapshot-input","spec-snapshot-copy","spec-snapshot-import","spec-handoff-status"];
  const refs=Object.fromEntries(ids.map(id=>[id,document.getElementById(id)]));
  if(ids.some(id=>!refs[id]))return;
  const unavailable=(message)=>{
    refs["spec-status"].textContent=message;
    for(const id of ["spec-copy","spec-reset","spec-undo","spec-redo","spec-device-desktop","spec-device-mobile","spec-snapshot-copy","spec-snapshot-import"])refs[id].disabled=true;
  };
  if(!engine||typeof engine.documentHTML!=="function"||typeof engine.normalize!=="function"
     ||typeof engine.snapshotJSON!=="function"||typeof engine.parseSnapshotJSON!=="function"
     ||!quality||typeof quality.audit!=="function"){
    unavailable("Offline compiler unavailable. No code generated.");
    return;
  }
  const themes=["obsidian","ivory","ocean"],layouts=["editorial","centered"];
  if(themes.some(t=>!Object.hasOwn(engine.PALETTES,t))||layouts.some(t=>!engine.LAYOUTS.includes(t))){
    unavailable("Design preset integrity check failed; no output generated.");
    return;
  }
  function el(tag,cls,value){
    const n=document.createElement(tag);
    if(cls)n.className=cls;
    if(value!==undefined)n.textContent=String(value);
    return n;
  }
  function wipe(node){while(node.firstChild)node.removeChild(node.firstChild);}
  const D=engine.DEFAULTS;
  for(const field of ["eyebrow","headline","description","action","palette","layout"])
    refs["spec-"+field].value=D[field];
  let device="desktop";
  const read=()=>({
    eyebrow:refs["spec-eyebrow"].value,headline:refs["spec-headline"].value,
    description:refs["spec-description"].value,action:refs["spec-action"].value,
    palette:refs["spec-palette"].value,layout:refs["spec-layout"].value
  });
  // Bounded, session-only history. Text edits become history entries on blur/change;
  // live input stays immediately previewable, and uncommitted edits can be reverted.
  const FIELDS=["eyebrow","headline","description","action","palette","layout"];
  const past=[],future=[];
  const HISTORY_LIMIT=24;
  let committed=engine.normalize(D);
  const equal=(a,b)=>FIELDS.every(field=>a[field]===b[field]);
  function isDirty(){
    // Normalization trims ordinary incidental whitespace; invalid input stays dirty
    // so Undo remains available to recover a safe committed snapshot.
    try{return !equal(engine.normalize(read()),committed);}
    catch(_){return FIELDS.some(field=>refs["spec-"+field].value!==committed[field]);}
  }
  function updateHistory(){
    const dirty=isDirty();
    refs["spec-undo"].disabled=!dirty&&past.length===0;
    refs["spec-redo"].disabled=dirty||future.length===0;
  }
  function commit(value){
    let next;
    try{next=engine.normalize(value);}catch(_){updateHistory();return false;}
    if(!equal(next,committed)){
      past.push(committed);
      if(past.length>HISTORY_LIMIT)past.shift();
      committed=next;
      future.length=0;
    }
    updateHistory();
    return true;
  }
  function restore(spec,notice){
    for(const field of FIELDS)refs["spec-"+field].value=spec[field];
    render();
    updateHistory();
    refs["spec-status"].textContent=notice;
  }
  function renderQuality(result){
    const summary=refs["spec-quality-status"];
    const out=refs["spec-quality-list"];
    const note=refs["spec-quality-warnings"];
    wipe(out);
    wipe(note);
    summary.textContent=result.passedChecks+"/"+result.staticCheckCount+
      " contrast pair checks passed · "+result.blockingChecks+
      " blocked. Manual review still required; this is not WCAG certification.";
    for(const c of result.checks){
      const entry=el("li","spec-quality-item");
      entry.append(el("span","",c.label),
        el("strong",c.status==="PASS"?"spec-quality-pass":"spec-quality-fail",
          c.ratio.toFixed(2)+":1 / "+c.minimum+":1 · "+c.status));
      out.append(entry);
    }
    for(const warning of result.warnings)note.append(el("li","",warning));
    if(result.warnings.length===0)note.append(el("li","","No preset-specific warnings. Real-browser, usability and accessibility review are still required."));
  }
  function render(){
    let spec,code,inspection;
    try {
      spec=engine.normalize(read());
      inspection=quality.audit(spec,engine);
      code=inspection.blockingChecks===0?engine.documentHTML(spec):"";
    } catch(error){
      wipe(refs["spec-preview"]);
      wipe(refs["spec-quality-list"]);
      wipe(refs["spec-quality-warnings"]);
      refs["spec-quality-status"].textContent="The static audit cannot evaluate invalid inputs.";
      refs["spec-export"].value="";
      refs["spec-copy"].disabled=true;
      refs["spec-snapshot-copy"].disabled=true;
      refs["spec-status"].textContent="Invalid fields. The preview and export have been cleared; nothing executed.";
      return;
    }
    renderQuality(inspection);
    if(inspection.blockingChecks!==0){
      wipe(refs["spec-preview"]);
      refs["spec-export"].value="";
      refs["spec-copy"].disabled=true;
      refs["spec-snapshot-copy"].disabled=true;
      refs["spec-status"].textContent="Static contrast gate blocked HTML export. Review the palette and try a compliant preset.";
      return;
    }
    const root=refs["spec-preview"];
    wipe(root);
    root.className="spec-preview spec-"+spec.palette+" spec-"+spec.layout+" spec-device-"+device;
    const p=engine.PALETTES[spec.palette];
    for(const [key,val] of Object.entries({background:p.background,surface:p.surface,ink:p.ink,muted:p.muted,accent:p.accent,line:p.line}))
      root.style.setProperty("--spec-"+key,val);
    const viewport=el("div","spec-viewport");
    const nav=el("div","spec-topbar");
    nav.append(el("strong","spec-brand","STUDIO / CONCEPT"),el("span","spec-toplabel","STATIC PREVIEW"));
    const main=el("div","spec-content");
    main.append(el("div","spec-kicker",spec.eyebrow),el("h3","spec-title",spec.headline),
      el("p","spec-lead",spec.description));
    const buttons=el("div","spec-buttons");
    // This is intentionally a static role-neutral mock CTA; no navigation occurs.
    buttons.append(el("span","spec-call-to-action",spec.action));
    main.append(buttons);
    const footer=el("div","spec-bottom","DETERMINISTIC DESIGN • NO AI MODEL • NO NETWORK");
    viewport.append(nav,main,footer);
    root.append(viewport);
    refs["spec-export"].value=code;
    refs["spec-copy"].disabled=false;
    refs["spec-snapshot-copy"].disabled=false;
    refs["spec-status"].textContent="Live local preview updated. Source is available below; no app, site or AI model was run.";
  }
  function updateDevice(next){
    if(!["desktop","mobile"].includes(next))return;
    device=next;
    refs["spec-device-desktop"].setAttribute("aria-pressed",String(next==="desktop"));
    refs["spec-device-mobile"].setAttribute("aria-pressed",String(next==="mobile"));
    render();
  }
  for(const field of FIELDS){
    const control=refs["spec-"+field];
    if(field==="palette"||field==="layout"){
      control.addEventListener("change",()=>{render();commit(read());});
    }else{
      control.addEventListener("input",()=>{render();updateHistory();});
      control.addEventListener("change",()=>{render();commit(read());});
    }
  }
  refs["spec-device-desktop"].addEventListener("click",()=>updateDevice("desktop"));
  refs["spec-device-mobile"].addEventListener("click",()=>updateDevice("mobile"));
  refs["spec-undo"].addEventListener("click",()=>{
    if(isDirty()){
      restore(committed,"Uncommitted local edits reverted. No source was published.");
      return;
    }
    if(!past.length)return;
    future.push(committed);
    committed=past.pop();
    restore(committed,"Previous local concept restored. No files or network changed.");
  });
  refs["spec-redo"].addEventListener("click",()=>{
    if(isDirty()||!future.length)return;
    past.push(committed);
    if(past.length>HISTORY_LIMIT)past.shift();
    committed=future.pop();
    restore(committed,"Next local concept restored. No files or network changed.");
  });
  refs["spec-reset"].addEventListener("click",()=>{
    commit(read());
    commit(D);
    for(const field of FIELDS)refs["spec-"+field].value=D[field];
    updateDevice("desktop");
    updateHistory();
    refs["spec-status"].textContent="Offline design restored to safe preset defaults. Undo can restore prior valid edits.";
  });
  refs["spec-copy"].addEventListener("click",async()=>{
    // User gesture only; no fallback network or file-system permissions.
    if(!refs["spec-export"].value){
      refs["spec-status"].textContent="There is no valid source code to copy.";
      return;
    }
    try{
      if(!navigator.clipboard||typeof navigator.clipboard.writeText!=="function")throw new Error("clipboard-unavailable");
      await navigator.clipboard.writeText(refs["spec-export"].value);
      refs["spec-status"].textContent="Static HTML source copied on request. No publishing or external action occurred.";
    }catch(_){
      refs["spec-export"].focus();
      refs["spec-export"].select();
      refs["spec-status"].textContent="Clipboard unavailable. Source selected for manual copy; no files or network changed.";
    }
  });
  refs["spec-snapshot-copy"].addEventListener("click",async()=>{
    let text;
    try{
      const candidate=engine.normalize(read());
      if(quality.audit(candidate,engine).blockingChecks!==0)throw new Error("contrast-failure");
      text=engine.snapshotJSON(candidate);
    }catch(_){
      refs["spec-handoff-status"].textContent="Cannot copy: fix invalid fields or blocked static contrast checks first.";
      return;
    }
    refs["spec-snapshot-input"].value=text;
    try{
      if(!navigator.clipboard||typeof navigator.clipboard.writeText!=="function")throw new Error("clipboard-unavailable");
      await navigator.clipboard.writeText(text);
      refs["spec-handoff-status"].textContent="Version 1 concept JSON copied by request. Nothing was saved, sent or published.";
    }catch(_){
      refs["spec-snapshot-input"].focus();
      refs["spec-snapshot-input"].select();
      refs["spec-handoff-status"].textContent="Clipboard unavailable. Concept JSON selected for manual copy; nothing was sent.";
    }
  });
  refs["spec-snapshot-import"].addEventListener("click",()=>{
    let incoming;
    try{
      incoming=engine.parseSnapshotJSON(refs["spec-snapshot-input"].value);
      if(quality.audit(incoming,engine).blockingChecks!==0)throw new Error("contrast-failure");
    }catch(_){
      refs["spec-handoff-status"].textContent="Import rejected: invalid, oversized, unsupported or noncompliant concept JSON. Current design unchanged.";
      return;
    }
    // The only mutation is to the in-memory validated editor. No file/asset/script import.
    commit(read());
    commit(incoming);
    restore(incoming,"Validated offline concept imported. Preview and static HTML regenerated locally.");
    refs["spec-handoff-status"].textContent="Version 1 concept imported locally. Undo can restore the preceding valid concept.";
  });
  updateDevice("desktop");
  updateHistory();
})();
