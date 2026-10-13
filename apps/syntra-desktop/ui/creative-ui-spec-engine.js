/* Offline deterministic UI composition; no imports, network, model calls or executable user code. */
((root) => {
  "use strict";
  const PALETTES = Object.freeze({
    obsidian: Object.freeze({background:"#0a1018",surface:"#12202b",ink:"#f3f8fb",muted:"#adc1cc",accent:"#8cf0d4",line:"#36505e"}),
    ivory: Object.freeze({background:"#f9f6ef",surface:"#ede7db",ink:"#21272c",muted:"#52636a",accent:"#235c7a",line:"#c9c1b5"}),
    ocean: Object.freeze({background:"#071a2b",surface:"#103149",ink:"#f0f8ff",muted:"#b0cedf",accent:"#7fd9ff",line:"#35617c"})
  });
  const LAYOUTS=Object.freeze(["editorial","centered"]);
  const DEFAULTS=Object.freeze({
    eyebrow:"DESIGNED WITH INTENTION",
    headline:"Build something worth remembering.",
    description:"A beautifully considered digital experience, built for people and made to feel effortless.",
    action:"Explore the experience",
    palette:"obsidian",
    layout:"editorial"
  });
  const LIMITS=Object.freeze({eyebrow:40,headline:80,description:240,action:32});
  function plainText(value,field){
    if(typeof value!=="string")throw new TypeError(field+" must be text");
    const cleaned=value.trim();
    if(!cleaned||cleaned.length>LIMITS[field]||/[\u0000-\u001f\u007f]/.test(cleaned))
      throw new RangeError("Invalid "+field+" (1–"+LIMITS[field]+" characters; no control characters)");
    return cleaned;
  }
  function normalize(input){
    if(!input||typeof input!=="object"||Array.isArray(input))throw new TypeError("spec must be an object");
    const allowed=new Set([...Object.keys(LIMITS),"palette","layout"]);
    if(Object.keys(input).some(key=>!allowed.has(key)))throw new TypeError("Unexpected spec field");
    const output={};
    for(const field of Object.keys(LIMITS))output[field]=plainText(input[field],field);
    if(!Object.prototype.hasOwnProperty.call(PALETTES,input.palette))throw new RangeError("Unapproved palette");
    if(!LAYOUTS.includes(input.layout))throw new RangeError("Unapproved layout");
    output.palette=input.palette;output.layout=input.layout;
    return Object.freeze(output);
  }
  function escapeHTML(value){
    return String(value).replace(/&/g,"&amp;").replace(/</g,"&lt;")
      .replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;");
  }
  function documentHTML(spec){
    const s=normalize(spec),c=PALETTES[s.palette];
    const align=s.layout==="centered"?"center":"left";
    const max=s.layout==="centered"?"850px":"770px";
    const css=[
      ":root{color-scheme:"+ (s.palette==="ivory"?"light":"dark")+";font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:"+c.ink+";background:"+c.background+"}",
      "*{box-sizing:border-box}html,body{margin:0;min-height:100%;}body{padding:clamp(22px,5vw,72px)}",
      ".top{display:flex;align-items:center;justify-content:space-between;gap:24px;border-bottom:1px solid "+c.line+";padding-bottom:23px}",
      ".brand{font-size:14px;font-weight:800;letter-spacing:.2em}.state{font-size:12px;letter-spacing:.12em;color:"+c.muted+"}",
      "main{display:flex;flex-direction:column;justify-content:center;min-height:min(750px,75vh);text-align:"+align+";max-width:"+max+";margin:"+(align==="center"?"0 auto":"0")+"}",
      ".eyebrow{font-size:12px;letter-spacing:.18em;font-weight:750;color:"+c.accent+";margin:40px 0 19px;overflow-wrap:anywhere}",
      "h1{font-family:Georgia,'Times New Roman',serif;font-size:clamp(40px,7vw,92px);letter-spacing:-.045em;line-height:1.1;font-weight:500;margin:0 0 24px;overflow-wrap:anywhere}",
      "p.lead{font-size:clamp(16px,1.65vw,20px);max-width:660px;line-height:1.7;color:"+c.muted+";overflow-wrap:anywhere;margin:"+(align==="center"?"0 auto 30px":"0 0 30px")+"}",
      ".action{font-weight:750;display:inline-flex;min-height:48px;align-items:center;justify-content:center;max-width:100%;padding:13px 22px;text-decoration:none;border-radius:9px;color:"+c.background+";background:"+c.accent+";font-size:16px;overflow-wrap:anywhere}",
      ".action:focus-visible{outline:3px solid "+c.ink+";outline-offset:4px}.actions{display:flex;justify-content:"+(align==="center"?"center":"flex-start")+"}",
      ".foot{font-size:12px;line-height:1.6;color:"+c.muted+";border-top:1px solid "+c.line+";padding-top:22px}",
      "@media(max-width:680px){body{padding:20px}main{min-height:70vh}.state{display:none}h1{font-size:clamp(38px,10vw,64px)}.action{width:100%;text-align:center}}",
      "@media(prefers-reduced-motion:reduce){*,*:before,*:after{animation:none!important;transition:none!important;scroll-behavior:auto!important}}"
    ].join("\n");
    // Completely inert one-file HTML; no JavaScript, imports, external URLs or asset loads.
    return [
      "<!doctype html>","<html lang=\"en\">","<head>",
      "<meta charset=\"utf-8\">","<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">",
      "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">",
      "<title>"+escapeHTML(s.headline)+"</title>","<style>"+css+"</style>","</head>","<body>",
      "<header class=\"top\"><span class=\"brand\">STUDIO / CONCEPT</span><span class=\"state\">STATIC OFFLINE PROTOTYPE</span></header>",
      "<main><div class=\"eyebrow\">"+escapeHTML(s.eyebrow)+"</div>",
      "<h1>"+escapeHTML(s.headline)+"</h1>",
      "<p class=\"lead\">"+escapeHTML(s.description)+"</p>",
      "<div class=\"actions\"><span class=\"action\" aria-label=\"Static call to action preview\">"+escapeHTML(s.action)+"</span></div></main>",
      "<footer class=\"foot\">Independent one-page HTML concept. No live backend, trackers, scripts or external assets.</footer>",
      "</body></html>"
    ].join("\n");
  }
  const api=Object.freeze({PALETTES,LAYOUTS,DEFAULTS,LIMITS,normalize,escapeHTML,documentHTML});
  if(typeof module!=="undefined"&&module.exports)module.exports=api;
  else Object.defineProperty(root,"SyntraUiSpecEngine",{value:api,writable:false,configurable:false});
})(typeof window!=="undefined"?window:{});
