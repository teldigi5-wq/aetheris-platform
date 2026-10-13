"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const quality=require("../apps/syntra-desktop/ui/design-quality-gates.js");
const data=JSON.parse(fs.readFileSync(path.resolve(__dirname,
  "../docs/design/WAYFARER_COLOR_USAGE.reference.json"),"utf8"));

test("Wayfarer reference brand tokens are fixed and cannot be promoted as production approval",()=>{
  assert.equal(data.product,"Wayfarer");
  assert.equal(data.status,"REVIEW_ONLY_NOT_PRODUCTION_TOKENS");
  assert.equal(data.palette.midnight,"#071421");
  assert.equal(data.palette.glacier,"#F6F8FA");
  assert.equal(data.palette.teal,"#21C6B7");
  assert.equal(data.palette.slate,"#8293A5");
  assert.equal(data.scope.browserUIAudited,false);
  assert.equal(data.scope.productionDeployed,false);
  assert.equal(data.scope.designApproved,false);
});
test("approved Wayfarer text pairs satisfy WCAG AA normal-text contrast 4.5:1",()=>{
  for(const p of data.approvedNormalTextPairs){
    const ratio=quality.contrast(data.palette[p.foreground],data.palette[p.background]);
    assert.ok(ratio>=4.5,p.role+" ratio "+ratio);
  }
});
test("reject light travel booking text colors that do not meet normal-text contrast",()=>{
  for(const p of data.prohibitedNormalTextPairs){
    const ratio=quality.contrast(data.palette[p.foreground],data.palette[p.background]);
    assert.ok(ratio<4.5,p.foreground+" incorrectly marked safe ("+ratio+")");
  }
  assert.ok(quality.contrast("#992E35",data.palette.glacier)>=4.5,
    "proposed dark red error text must meet WCAG AA on glacier");
});
test("CTA text/fill combinations pass, but light-page CTA requires visible boundary",()=>{
  const tokens=data.palette;
  const specs=data.recommendations;
  assert.ok(quality.contrast(tokens[specs.lightSurfacePrimaryButtonLabel],
    tokens[specs.lightSurfacePrimaryButtonFill])>=4.5);
  assert.ok(quality.contrast(tokens[specs.darkSurfacePrimaryButtonLabel],
    tokens[specs.darkSurfacePrimaryButtonFill])>=4.5);
  assert.ok(quality.contrast(tokens.teal,tokens.glacier)<3);
  assert.ok(quality.contrast(tokens.midnight,tokens.glacier)>=3);
});
