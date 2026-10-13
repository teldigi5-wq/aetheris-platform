"""Safety contract for Syntra read-only Creative Studio; no actual AI or GPU calls."""
import json
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"apps"/"syntra-desktop"
EXPECTED={"ui-ux-audit","motion-experience-review","3d-web-experience-review",
          "frontend-quality-review","creative-production-review"}
REPOS={"mrdoob/three.js","pmndrs/react-three-fiber","motiondivision/motion","greensock/GSAP",
"storybookjs/storybook","tailwindlabs/tailwindcss","dequelabs/axe-core","microsoft/playwright",
"blender/blender","remotion-dev/remotion","figma/plugin-samples","ffmpegwasm/ffmpeg.wasm"}

class CreativeStudioContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html=(ROOT/"ui/index.html").read_text()
        cls.js=(ROOT/"ui/creative-workbench.js").read_text()
        cls.catalogjs=(ROOT/"ui/creative-catalog.js").read_text()
        cls.css=(ROOT/"ui/styles.css").read_text()
        cls.app=(ROOT/"ui/app.js").read_text()
        cls.contract=json.loads((ROOT/"creative-studio.contract.json").read_text())
        cls.catalog=json.loads(cls.catalogjs.split("const snapshot=",1)[1].split(";\n  for",1)[0])

    def test_catalog_has_five_review_skills_and_twelve_uninstalled_sources(self):
        c=self.catalog
        self.assertEqual(1,c["schemaVersion"])
        self.assertEqual("STATIC_REVIEW_WORKBENCH",c["mode"])
        self.assertTrue(re.fullmatch("[0-9a-f]{40}",c["sourceRuntimeSha"]))
        self.assertEqual("BLOCKED_PENDING_HARDWARE",c["physicalPcStatus"])
        self.assertEqual(EXPECTED,{s["id"] for s in c["skills"]})
        self.assertEqual(REPOS,{s["repository"] for s in c["sources"]})
        for skill in c["skills"]:
            self.assertEqual("TEMPLATE_ONLY",skill["status"])
            for key in ("installed","executionEnabled","toolGrant"):
                self.assertIs(skill[key],False)
        for src in c["sources"]:
            self.assertEqual("https://github.com/"+src["repository"],src["sourceUrl"])
            self.assertEqual("NOT_INSTALLED",src["status"])
            self.assertEqual("UNTRUSTED_EXTERNAL",src["trust"])
            self.assertFalse(src["licenseReviewed"])
            self.assertFalse(src["enabled"])

    def test_interface_and_navigation_are_present(self):
        for marker in ('data-view="creative"', 'data-section="creative"',
                       'id="creative-skill-list"','id="creative-form"',
                       'id="creative-subject"','id="creative-plan"',
                       'id="creative-sources"','id="creative-status"'):
            self.assertIn(marker,self.html)
        self.assertIn('creative: "Creative Studio"',self.app)
        self.assertIn('event.shiftKey && event.key.toLowerCase() === "c"',self.app)
        self.assertIn('aria-pressed',self.js)
        self.assertIn('prefers-reduced-motion:reduce',self.css)
        self.assertIn('src="./creative-catalog.js"',self.html)
        self.assertIn('src="./creative-workbench.js"',self.html)

    def test_no_runtime_or_untrusted_html_execution(self):
        for needle in ("fetch(","XMLHttpRequest","eval(","innerHTML","outerHTML",
                       "localStorage","sessionStorage","__TAURI__","WebSocket",
                       "import(","window.open(","document.write","navigator.sendBeacon"):
            with self.subTest(needle=needle):
                self.assertNotIn(needle,self.js)
                self.assertNotIn(needle,self.catalogjs)
        self.assertIn("textContent",self.js)
        self.assertIn("Object.freeze",self.catalogjs)
        self.assertIn('NO', 'NO')

    def test_contract_does_not_overclaim(self):
        c=self.contract
        self.assertEqual(5,c["skills"])
        self.assertEqual(12,c["libraryReferences"])
        self.assertEqual("LOCAL_CREATIVE_REVIEW_ONLY",c["mode"])
        self.assertEqual("DETERMINISTIC_OFFLINE_CHECKLIST",c["sessionPlanType"])
        self.assertEqual("BLOCKED_PENDING_HARDWARE",c["hardwareStatus"])
        for key in ("noAiInference","noImageGeneration","noVideoGeneration","noFigmaWrites",
                    "noBlenderExecution","noBrowserAutomation","noExternalNetwork",
                    "noPackageInstallation","noPluginActivation","noTauriCommandInvocation",
                    "noPromptPersistence","ownerApprovalRequiredForAnyFutureWrite"):
            self.assertIs(c[key],True,key)
        self.assertFalse(c["realRenderPerformanceVerified"])

if __name__=="__main__":
    unittest.main()
