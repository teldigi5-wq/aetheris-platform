"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const quality=require("../apps/syntra-desktop/ui/design-quality-gates.js");
const engine=require("../apps/syntra-desktop/ui/creative-ui-spec-engine.js");

test("WCAG color math anchors: black/white 21:1; near-threshold gray remains a failure",()=>{
  assert.equal(quality.contrast("#000000","#ffffff"),21);
  assert.ok(quality.contrast("#777777","#ffffff")<4.5);
  assert.ok(quality.contrast("#767676","#ffffff")>=4.5);
  for(const bad of ["transparent","#abc","#zzzzzz","url(/x)","rgb(0,0,0)"]){
    assert.throws(()=>quality.contrast(bad,"#ffffff"),TypeError);
  }
});

test("all studio presets meet seven static contrast checks, never claim certification",()=>{
  for(const palette of ["obsidian","ivory","ocean"]){
    for(const layout of ["editorial","centered"]){
      const report=quality.audit({...engine.DEFAULTS,palette,layout},engine);
      assert.equal(report.staticCheckCount,7);
      assert.equal(report.passedChecks,7,JSON.stringify({palette,layout,checks:report.checks}));
      assert.equal(report.blockingChecks,0);
      assert.equal(report.result,"STATIC_CHECKS_PASSED_MANUAL_AUDIT_REQUIRED");
      assert.equal(report.certified,false);
      assert.equal(report.realBrowserTested,false);
      assert.equal(report.measuredPerformance,false);
      assert.equal(report.reviewRequired,true);
      assert.equal(report.physicalPcStatus,"BLOCKED_PENDING_HARDWARE");
      assert.ok(Object.isFrozen(report));
      assert.ok(Object.isFrozen(report.checks));
    }
  }
});

test("contrast gate catches accessible UI violations even in a branded palette",()=>{
  const palette={...engine.PALETTES.obsidian,muted:"#555555"};
  const other={...engine,PALETTES:{...engine.PALETTES,obsidian:palette}};
  const r=quality.audit(engine.DEFAULTS,other);
  assert.ok(r.blockingChecks>=2);
  assert.equal(r.result,"BLOCKED_STATIC_CONTRAST");
  assert.ok(r.checks.some(c=>c.id==="secondary-text"&&c.status==="BLOCK"));
});

test("headline length, verbose descriptions, long CTAs generate explicit review warnings",()=>{
  const r=quality.audit({
    ...engine.DEFAULTS,headline:"H".repeat(70),description:"D".repeat(200),action:"A".repeat(30)
  },engine);
  assert.ok(r.warnings.some(x=>x.includes("headline")));
  assert.ok(r.warnings.some(x=>x.includes("copy")));
  assert.ok(r.warnings.some(x=>x.includes("CTA")));
  assert.ok(r.manualChecks.some(x=>x.includes("200%")));
  assert.ok(r.manualChecks.some(x=>x.includes("screen reader")));
});

test("quality standard is conservative, but does not invent user or hardware tests",()=>{
  const t=quality.DESIGN_TOKENS;
  assert.deepEqual(t.spacingPx,[4,8,12,16,24,32,48,64]);
  assert.deepEqual(t.responsiveReviewWidthsPx,[320,360,390,768,1024,1440]);
  assert.equal(t.pointerTargetPx,44);
  assert.equal(t.typePx.body,16);
  assert.equal(t.requiredReflowWidthPx,320);
  assert.equal(t.motionMs.transition,220);
  assert.equal(t.nonEssentialAutoMotionAllowed,false);
  assert.equal(t.reducedMotionFallbackRequired,true);
  assert.equal(t.independentlyVerified,false);
  assert.ok(quality.TEXT_AA===4.5&&quality.NON_TEXT_AA===3);
});
