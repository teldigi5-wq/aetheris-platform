/* Live, offline DOM preview for a bounded static HTML concept. No agents, tools or network. */
(() => {
  "use strict";
  const engine=window.SyntraUiSpecEngine;
  const ids=["spec-eyebrow","spec-headline","spec-description","spec-action","spec-palette",
    "spec-layout","spec-preview","spec-export","spec-status","spec-copy",
    "spec-reset","spec-device-desktop","spec-device-mobile"];
  const refs=Object.fromEntries(ids.map(id=>[id,document.getElementById(id)]));
  if(ids.some(id=>!refs[id]))return;
  const unavailable=(message)=>{
    refs["spec-status"].textContent=message;
    for(const id of ["spec-copy","spec-reset","spec-device-desktop","spec-device-mobile"])refs[id].disabled=true;
  };
  if(!engine||typeof engine.documentHTML!=="function"||typeof engine.normalize!=="function"){
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
  function render(){
    let spec,code;
    try {spec=engine.normalize(read());code=engine.documentHTML(spec);}
    catch(error){
      wipe(refs["spec-preview"]);
      refs["spec-export"].value="";
      refs["spec-copy"].disabled=true;
      refs["spec-status"].textContent="Invalid fields. The preview and export have been cleared; nothing executed.";
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
    refs["spec-status"].textContent="Live local preview updated. Source is available below; no app, site or AI model was run.";
  }
  function updateDevice(next){
    if(!["desktop","mobile"].includes(next))return;
    device=next;
    refs["spec-device-desktop"].setAttribute("aria-pressed",String(next==="desktop"));
    refs["spec-device-mobile"].setAttribute("aria-pressed",String(next==="mobile"));
    render();
  }
  for(const field of ["eyebrow","headline","description","action","palette","layout"])
    refs["spec-"+field].addEventListener(field==="palette"||field==="layout"?"change":"input",render);
  refs["spec-device-desktop"].addEventListener("click",()=>updateDevice("desktop"));
  refs["spec-device-mobile"].addEventListener("click",()=>updateDevice("mobile"));
  refs["spec-reset"].addEventListener("click",()=>{
    for(const field of ["eyebrow","headline","description","action","palette","layout"])refs["spec-"+field].value=D[field];
    updateDevice("desktop");
    refs["spec-status"].textContent="Offline design restored to safe preset defaults.";
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
  updateDevice("desktop");
})();
