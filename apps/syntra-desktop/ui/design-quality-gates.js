/* Offline UI design-quality checks: limited static checks, NOT WCAG certification.
 * No network, package installation, browser audit, rendering, or AI inference.
 */
((root) => {
  "use strict";
  const TEXT_AA = 4.5;
  const LARGE_TEXT_AA = 3;
  const NON_TEXT_AA = 3;
  const DESIGN_TOKENS = Object.freeze({
    spacingPx: Object.freeze([4,8,12,16,24,32,48,64]),
    typePx: Object.freeze({body:16,bodyCompact:14,caption:12,displayMin:40}),
    lineHeight: Object.freeze({body:1.6,headings:1.12}),
    pointerTargetPx: 44,
    requiredReflowWidthPx: 320,
    responsiveReviewWidthsPx: Object.freeze([320,360,390,768,1024,1440]),
    motionMs: Object.freeze({feedback:120,transition:220,emphasis:380}),
    nonEssentialAutoMotionAllowed:false,
    reducedMotionFallbackRequired:true,
    independentlyVerified:false
  });
  function rgb(hex){
    if(typeof hex!=="string"||!/^#[0-9a-fA-F]{6}$/.test(hex))throw new TypeError("A strict six-digit hex color is required");
    return [1,3,5].map(n=>parseInt(hex.slice(n,n+2),16)/255)
      .map(c=>c<=0.04045?c/12.92:((c+0.055)/1.055)**2.4);
  }
  function luminance(hex){
    const [r,g,b]=rgb(hex);
    return r*0.2126+g*0.7152+b*0.0722;
  }
  function contrast(a,b){
    const x=luminance(a),y=luminance(b);
    return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);
  }
  function check(id,label,ratio,min,scope){
    const result={id,label,ratio:Number(ratio.toFixed(3)),minimum:min,
      status:ratio>=min?"PASS":"BLOCK",scope};
    return Object.freeze(result);
  }
  function audit(spec,engine){
    if(!engine||typeof engine.normalize!=="function"||!engine.PALETTES)throw new TypeError("approved design engine required");
    const s=engine.normalize(spec);
    const p=engine.PALETTES[s.palette];
    if(!p||Object.values(p).some(x=>!/^#[0-9a-fA-F]{6}$/.test(x)))throw new TypeError("unknown or invalid palette");
    const checks=[
      check("body-text","Primary body text",contrast(p.ink,p.background),TEXT_AA,"WCAG 2.2 AA 1.4.3"),
      check("secondary-text","Muted secondary text",contrast(p.muted,p.background),TEXT_AA,"WCAG 2.2 AA 1.4.3"),
      check("on-surface","Primary text on elevated surface",contrast(p.ink,p.surface),TEXT_AA,"WCAG 2.2 AA 1.4.3"),
      check("secondary-on-surface","Muted text on elevated surface",contrast(p.muted,p.surface),TEXT_AA,"WCAG 2.2 AA 1.4.3"),
      check("eyebrow","Accent eyebrow label",contrast(p.accent,p.background),TEXT_AA,"WCAG 2.2 AA 1.4.3"),
      check("cta-text","CTA label against accent",contrast(p.background,p.accent),TEXT_AA,"WCAG 2.2 AA 1.4.3"),
      check("focus-ring","Focus cue against background",contrast(p.ink,p.background),NON_TEXT_AA,"WCAG 2.2 AA 1.4.11 (one color pair only)")
    ];
    const hardFailures=checks.filter(x=>x.status==="BLOCK");
    const warnings=[];
    if(s.headline.length>65)warnings.push("Long display headline: inspect 320px wrapping and 200% text zoom.");
    if(s.description.length>180)warnings.push("Long supporting copy: verify comfortable line length and reading hierarchy.");
    if(s.action.length>26)warnings.push("Long CTA: manually verify translations and narrow mobile widths.");
    const borderRatio=contrast(p.line,p.background);
    if(borderRatio<NON_TEXT_AA)warnings.push("Subtle border contrast below 3:1; do not rely on this line alone to identify a required interactive component.");
    const manual=[
      "Test 320/360/390/768/1024/1440 CSS-pixel widths and 200% text zoom without 2D scrolling.",
      "Verify keyboard navigation, focus visibility, headings, labels and screen reader behavior in a real browser.",
      "Check pointer target sizes and spacing: WCAG 2.2 AA minimum 24px with exceptions; use 44px as this studio's conservative design target.",
      "Verify user-selected reduced motion and prevent non-essential auto-animations lasting longer than five seconds without controls.",
      "Assess imagery, actual brand font assets, human usability, loading/error/empty states, language expansion and device performance.",
      "Evaluate meaningful component boundaries against adjacent surfaces; static color-pair checks are not full WCAG conformance."
    ];
    return Object.freeze({
      version:1,
      scope:"STATIC_DESIGN_TOKENS_ONLY",
      palette:s.palette,layout:s.layout,
      result:hardFailures.length?"BLOCKED_STATIC_CONTRAST":"STATIC_CHECKS_PASSED_MANUAL_AUDIT_REQUIRED",
      staticCheckCount:checks.length,
      passedChecks:checks.length-hardFailures.length,
      blockingChecks:hardFailures.length,
      checks:Object.freeze(checks),
      warnings:Object.freeze(warnings),
      manualChecks:Object.freeze(manual),
      reviewRequired:true,certified:false,realBrowserTested:false,measuredPerformance:false,
      physicalPcStatus:"BLOCKED_PENDING_HARDWARE"
    });
  }
  const api=Object.freeze({DESIGN_TOKENS,TEXT_AA,LARGE_TEXT_AA,NON_TEXT_AA,luminance,contrast,audit});
  if(typeof module!=="undefined"&&module.exports)module.exports=api;
  else Object.defineProperty(root,"SyntraDesignQuality",{value:api,writable:false,configurable:false});
})(typeof window!=="undefined"?window:{});
