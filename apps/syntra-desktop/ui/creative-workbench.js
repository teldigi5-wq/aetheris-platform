/* Creative Studio planning workbench. No AI inference, plugins or live code generation. */
(() => {
  "use strict";
  const catalog=window.SyntraCreativeCatalog;
  const skillList=document.getElementById("creative-skill-list");
  const sourceList=document.getElementById("creative-sources");
  const sourceQuery=document.getElementById("creative-source-search");
  const filter=document.getElementById("creative-source-category");
  const subject=document.getElementById("creative-subject");
  const result=document.getElementById("creative-plan");
  const count=document.getElementById("creative-source-count");
  const truth=document.getElementById("creative-status");
  const clear=document.getElementById("creative-clear");
  const form=document.getElementById("creative-form");
  if(!skillList||!sourceList||!sourceQuery||!filter||!subject||!result||!count||!truth||!clear||!form)return;

  const approvedIds=new Set(["ui-ux-audit","motion-experience-review","3d-web-experience-review",
    "frontend-quality-review","creative-production-review"]);
  const approvedRepositories=new Set([
    "mrdoob/three.js","pmndrs/react-three-fiber","motiondivision/motion","greensock/GSAP",
    "storybookjs/storybook","tailwindlabs/tailwindcss","dequelabs/axe-core","microsoft/playwright",
    "blender/blender","remotion-dev/remotion","figma/plugin-samples","ffmpegwasm/ffmpeg.wasm"
  ]);
  const valid=Boolean(catalog&&catalog.schemaVersion===1&&catalog.mode==="STATIC_REVIEW_WORKBENCH"
    &&catalog.physicalPcStatus==="BLOCKED_PENDING_HARDWARE"
    &&catalog.executionEnabled===false&&catalog.networkFetchEnabled===false
    &&catalog.paidProvidersEnabled===false
    &&Array.isArray(catalog.skills)&&catalog.skills.length===5
    &&Array.isArray(catalog.sources)&&catalog.sources.length===12
    &&catalog.skills.every(s=>approvedIds.has(s.id)&&s.status==="TEMPLATE_ONLY"
      &&s.executionEnabled===false&&s.installed===false&&s.toolGrant===false
      &&Array.isArray(s.deliverables)&&Array.isArray(s.needs))
    &&new Set(catalog.skills.map(s=>s.id)).size===5
    &&catalog.sources.every(s=>approvedRepositories.has(s.repository)
      &&s.sourceUrl==="https://github.com/"+s.repository
      &&s.status==="NOT_INSTALLED"&&s.enabled===false&&s.licenseReviewed===false
      &&s.trust==="UNTRUSTED_EXTERNAL")
    &&new Set(catalog.sources.map(s=>s.repository)).size===12);
  if(!valid){
    truth.textContent="Creative Studio catalog integrity check failed. Planning is unavailable.";
    for(const input of [sourceQuery,filter,subject,clear,document.getElementById("creative-plan-submit")])input.disabled=true;
    return;
  }

  function node(tag,cls,txt){const el=document.createElement(tag);if(cls)el.className=cls;if(txt!==undefined)el.textContent=String(txt);return el;}
  function wipe(n){while(n.firstChild)n.removeChild(n.firstChild);}
  function appendFacts(target,arr){const ul=node("ul","creative-checks");arr.forEach(v=>ul.append(node("li","",v)));target.append(ul);}
  let active=catalog.skills[0].id,briefCount=0;

  function renderSkills(){
    wipe(skillList);
    for(const s of catalog.skills){
      const btn=node("button","creative-skill"+(active===s.id?" selected":""));
      btn.type="button";btn.setAttribute("aria-pressed",String(active===s.id));
      btn.setAttribute("aria-label","Select "+s.title+" review template");
      btn.append(node("span","creative-skill-icon",{
        "ui-ux-audit":"◫","motion-experience-review":"✦","3d-web-experience-review":"◇",
        "frontend-quality-review":"⌘","creative-production-review":"◉"
      }[s.id]));
      const label=node("span","creative-skill-text");
      label.append(node("strong","",s.title),node("small","",s.description));
      btn.append(label,node("span","creative-skill-meta","REVIEW"));
      btn.addEventListener("click",()=>{
        active=s.id;renderSkills();renderSelection();
        truth.textContent=s.title+" selected. This is an offline planning template, not an active AI agent.";
      });
      skillList.append(btn);
    }
  }
  function renderSelection(){
    const s=catalog.skills.find(x=>x.id===active);
    document.getElementById("creative-selected-title").textContent=s.title;
    document.getElementById("creative-selected-summary").textContent=s.description;
    const checklist=document.getElementById("creative-deliverables");
    wipe(checklist);appendFacts(checklist,s.deliverables);
  }
  function renderSources(){
    const term=sourceQuery.value.trim().toLocaleLowerCase();
    const category=filter.value;
    const found=catalog.sources.filter(s=>
      (!term||[s.name,s.repository,s.category,s.reportedLicense].join(" ").toLocaleLowerCase().includes(term))
      &&(category==="all"||category===s.category));
    count.textContent=found.length+" of 12 references • none installed";
    wipe(sourceList);
    if(!found.length){sourceList.append(node("p","creative-source-empty","No matching sources. They are references, not available installed tools."));return;}
    for(const s of found){
      const item=node("article","creative-source");
      const caption=node("div","creative-source-text");
      caption.append(node("strong","",s.name),node("small","",s.category.replaceAll("-"," ")+" · GitHub license label: "+s.reportedLicense));
      const status=node("span","creative-source-state","UNINSTALLED");
      item.append(caption,status);
      item.title=s.sourceUrl+" — requires license, source revision and permission review";
      sourceList.append(item);
    }
  }
  function createPlan(e){
    e.preventDefault();
    const text=subject.value.trim();
    if(!text||text.length>600){
      truth.textContent="Describe your design goal (up to 600 characters). No review plan created.";
      return;
    }
    const s=catalog.skills.find(x=>x.id===active);
    wipe(result);
    const h=node("div","creative-plan-header");
    h.append(node("strong","",s.title+" — Review plan"),node("span","creative-tag","OFFLINE TEMPLATE"));
    result.append(h);
    result.append(node("p","creative-plan-brief",text));
    result.append(node("h4","","Suggested review outputs"));
    appendFacts(result,s.deliverables);
    result.append(node("h4","","Evidence needed"));
    appendFacts(result,s.needs);
    result.append(node("p","creative-plan-truth",
      "This is an offline checklist derived from a static template. No AI analysis, Figma edit, 3D rendering, GSAP animation, video generation, repository write or network request occurred."));
    briefCount++;
    truth.textContent="Offline review checklist prepared in this app session. Count: "+briefCount+". No agent executed.";
  }

  const categories=[...new Set(catalog.sources.map(s=>s.category))].sort();
  categories.forEach(value=>{const option=node("option","",value.replaceAll("-"," "));option.value=value;filter.append(option);});
  sourceQuery.addEventListener("input",renderSources);
  filter.addEventListener("change",renderSources);
  form.addEventListener("submit",createPlan);
  clear.addEventListener("click",()=>{
    subject.value="";wipe(result);result.append(node("p","creative-plan-empty","Your private brief stays in this window only. Choose a skill and prepare a new checklist."));
    truth.textContent="Session-only creative brief cleared. No remote or installed tool state changed.";
  });
  renderSkills();renderSelection();renderSources();
})();
