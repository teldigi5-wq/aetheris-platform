/* Non-executable Creative Studio catalog. Exact draft source: be8dcaf51ca347d8f1c9f2c8187a04b78689a43f. */
(() => {
  "use strict";
  const snapshot={
  "schemaVersion": 1,
  "mode": "STATIC_REVIEW_WORKBENCH",
  "sourceRuntimePr": "https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/20",
  "sourceRuntimeSha": "be8dcaf51ca347d8f1c9f2c8187a04b78689a43f",
  "physicalPcStatus": "BLOCKED_PENDING_HARDWARE",
  "executionEnabled": false,
  "networkFetchEnabled": false,
  "paidProvidersEnabled": false,
  "skills": [
    {
      "id": "ui-ux-audit",
      "title": "UI / UX Design",
      "description": "Map journeys, layouts, design systems and responsive interactions.",
      "deliverables": [
        "User journey / information hierarchy",
        "Design tokens and component consistency",
        "Responsive and keyboard-accessibility checklist"
      ],
      "needs": [
        "Owner-authorized interface evidence",
        "Product objectives and target devices"
      ],
      "status": "TEMPLATE_ONLY",
      "installed": false,
      "executionEnabled": false,
      "toolGrant": false
    },
    {
      "id": "motion-experience-review",
      "title": "Motion Graphics",
      "description": "Plan cinematic transitions, micro-interactions and reduced-motion fallbacks.",
      "deliverables": [
        "Choreography matrix: trigger, state and fallback",
        "Reduced-motion and interruption specification",
        "Mobile performance testing plan"
      ],
      "needs": [
        "Screen flow, animation intent and device targets"
      ],
      "status": "TEMPLATE_ONLY",
      "installed": false,
      "executionEnabled": false,
      "toolGrant": false
    },
    {
      "id": "3d-web-experience-review",
      "title": "3D Experiences",
      "description": "Structure premium 3D scenes, realistic Earth experiences and fallbacks.",
      "deliverables": [
        "Scene graph, camera and lighting intentions",
        "Texture/draw-call budgeting assumptions",
        "Accessible non-WebGL and touch fallback"
      ],
      "needs": [
        "Scene requirements, assets and target hardware"
      ],
      "status": "TEMPLATE_ONLY",
      "installed": false,
      "executionEnabled": false,
      "toolGrant": false
    },
    {
      "id": "frontend-quality-review",
      "title": "Frontend Quality",
      "description": "Review responsiveness, accessibility, loading and performance.",
      "deliverables": [
        "Prioritized quality risks and reproductions",
        "Keyboard, focus, zoom and contrast checklist",
        "Performance and regression-test plan"
      ],
      "needs": [
        "Source or screenshots with authorization"
      ],
      "status": "TEMPLATE_ONLY",
      "installed": false,
      "executionEnabled": false,
      "toolGrant": false
    },
    {
      "id": "creative-production-review",
      "title": "Creative Production",
      "description": "Plan storyboards, exports and media-rights reviews.",
      "deliverables": [
        "Shot / scene and caption plan",
        "Asset provenance and license assessment",
        "Export format and verification checklist"
      ],
      "needs": [
        "Creative brief, destination channels and asset rights"
      ],
      "status": "TEMPLATE_ONLY",
      "installed": false,
      "executionEnabled": false,
      "toolGrant": false
    }
  ],
  "sources": [
    {
      "id": "three-js",
      "category": "3d-rendering",
      "name": "three.js",
      "repository": "mrdoob/three.js",
      "sourceUrl": "https://github.com/mrdoob/three.js",
      "reportedLicense": "MIT",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "react-three-fiber",
      "category": "3d-rendering",
      "name": "react-three-fiber",
      "repository": "pmndrs/react-three-fiber",
      "sourceUrl": "https://github.com/pmndrs/react-three-fiber",
      "reportedLicense": "MIT",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "motion",
      "category": "motion-ui",
      "name": "motion",
      "repository": "motiondivision/motion",
      "sourceUrl": "https://github.com/motiondivision/motion",
      "reportedLicense": "MIT",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "gsap",
      "category": "motion-ui",
      "name": "GSAP",
      "repository": "greensock/GSAP",
      "sourceUrl": "https://github.com/greensock/GSAP",
      "reportedLicense": "NOASSERTION",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "storybook",
      "category": "ui-ux-components",
      "name": "storybook",
      "repository": "storybookjs/storybook",
      "sourceUrl": "https://github.com/storybookjs/storybook",
      "reportedLicense": "MIT",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "tailwind",
      "category": "ui-ux-components",
      "name": "tailwindcss",
      "repository": "tailwindlabs/tailwindcss",
      "sourceUrl": "https://github.com/tailwindlabs/tailwindcss",
      "reportedLicense": "MIT",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "axe-core",
      "category": "accessibility",
      "name": "axe-core",
      "repository": "dequelabs/axe-core",
      "sourceUrl": "https://github.com/dequelabs/axe-core",
      "reportedLicense": "MPL-2.0",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "playwright",
      "category": "accessibility",
      "name": "playwright",
      "repository": "microsoft/playwright",
      "sourceUrl": "https://github.com/microsoft/playwright",
      "reportedLicense": "Apache-2.0",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "blender",
      "category": "creative-3d-authoring",
      "name": "blender",
      "repository": "blender/blender",
      "sourceUrl": "https://github.com/blender/blender",
      "reportedLicense": "NOASSERTION",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "remotion",
      "category": "motion-video",
      "name": "remotion",
      "repository": "remotion-dev/remotion",
      "sourceUrl": "https://github.com/remotion-dev/remotion",
      "reportedLicense": "NOASSERTION",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "figma-samples",
      "category": "design-integration",
      "name": "plugin-samples",
      "repository": "figma/plugin-samples",
      "sourceUrl": "https://github.com/figma/plugin-samples",
      "reportedLicense": "MIT",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    },
    {
      "id": "ffmpeg-wasm",
      "category": "motion-video",
      "name": "ffmpeg.wasm",
      "repository": "ffmpegwasm/ffmpeg.wasm",
      "sourceUrl": "https://github.com/ffmpegwasm/ffmpeg.wasm",
      "reportedLicense": "MIT",
      "status": "NOT_INSTALLED",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReviewed": false,
      "enabled": false
    }
  ]
};
  for(const skill of snapshot.skills){Object.freeze(skill.deliverables);Object.freeze(skill.needs);Object.freeze(skill);}
  for(const source of snapshot.sources)Object.freeze(source);
  Object.freeze(snapshot.skills);Object.freeze(snapshot.sources);Object.freeze(snapshot);
  Object.defineProperty(window,"SyntraCreativeCatalog",{value:snapshot,writable:false,configurable:false});
})();
