"""Static safety/UX integration tests for bounded native-free Syntra design preview."""
import json
import unittest
from pathlib import Path

APP=Path(__file__).resolve().parents[1]/"apps"/"syntra-desktop"
UI=APP/"ui"

class UiConceptBuilderContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract=json.loads((APP/"ui-spec.contract.json").read_text(encoding="utf-8"))
        cls.html=(UI/"index.html").read_text(encoding="utf-8")
        cls.js=(UI/"creative-ui-preview.js").read_text(encoding="utf-8")
        cls.compiler=(UI/"creative-ui-spec-engine.js").read_text(encoding="utf-8")
        cls.styles=(UI/"styles.css").read_text(encoding="utf-8")
        cls.quality=(UI/"design-quality-gates.js").read_text(encoding="utf-8")

    def test_constrained_feature_scope(self):
        c=self.contract
        self.assertEqual(1,c["schemaVersion"])
        self.assertEqual("DETERMINISTIC_LOCAL_PREVIEW_AND_STATIC_HTML_EXPORT",c["mode"])
        self.assertEqual("BLOCKED_PENDING_HARDWARE",c["physicalPcStatus"])
        self.assertEqual(["obsidian","ivory","ocean"],c["palettePresets"])
        self.assertEqual(["editorial","centered"],c["layoutPresets"])
        for flag in ("desktopPreviewEnabled","mobilePreviewEnabled","codeCopyOnlyOnUserGesture",
                     "userTextHtmlEscaped","generatedHtmlContentSecurityPolicy","generatedHtmlNoScripts"):
            self.assertIs(c[flag],True,flag)
        for flag in ("externalNetworkEnabled","modelInferenceEnabled","interactiveSiteDeploymentEnabled",
                     "figmaWriteEnabled","thirdPartyCodeInstallEnabled","gpuRenderingEnabled",
                     "runtimeCommandAccessEnabled","paidProvidersEnabled","briefPersistenceEnabled",
                     "executionSuccessClaimed"):
            self.assertIs(c[flag],False,flag)

    def test_user_controls_and_semantics(self):
        for element_id in ("spec-eyebrow","spec-headline","spec-description","spec-action",
                           "spec-palette","spec-layout","spec-preview","spec-export",
                           "spec-device-desktop","spec-device-mobile","spec-copy","spec-reset","spec-status"):
            self.assertIn(f'id="{element_id}"',self.html)
        self.assertIn('aria-pressed="true"',self.html)
        self.assertIn("Live UI concept builder",self.html)
        self.assertIn('id="spec-export" class="spec-source" readonly',self.html)
        self.assertIn("offline",self.html.lower())
        self.assertIn('src="./creative-ui-spec-engine.js"',self.html)
        self.assertIn('src="./creative-ui-preview.js"',self.html)
        self.assertLess(self.html.index('src="./creative-ui-spec-engine.js"'),
                        self.html.index('src="./creative-ui-preview.js"'))
        self.assertIn("prefers-reduced-motion:reduce",self.styles)
        for element_id in ("spec-quality-status","spec-quality-list","spec-quality-warnings"):
            self.assertIn(f'id="{element_id}"',self.html)
        self.assertLess(self.html.index('src="./creative-ui-spec-engine.js"'),
                        self.html.index('src="./design-quality-gates.js"'))
        self.assertLess(self.html.index('src="./design-quality-gates.js"'),
                        self.html.index('src="./creative-ui-preview.js"'))
        self.assertIn("NOT CERTIFIED",self.html)
        self.assertIn("Human QA is still mandatory",self.html)


    def test_preview_never_executes_or_stores_user_content(self):
        for source in (self.js,self.compiler,self.quality):
            for unsafe in ("fetch(","XMLHttpRequest","WebSocket","EventSource","localStorage",
                           "sessionStorage","__TAURI__","eval(","innerHTML","outerHTML",
                           "document.write","insertAdjacentHTML","navigator.sendBeacon",
                           "window.open(","import("):
                with self.subTest(unsafe=unsafe):
                    self.assertNotIn(unsafe,source)
        self.assertIn("textContent",self.js)
        self.assertIn("document.createElement",self.js)
        self.assertIn('const PALETTES = Object.freeze(',self.compiler)
        self.assertIn('default-src',self.compiler)
        self.assertIn('escapeHTML(s.headline)',self.compiler)
        self.assertIn('escapeHTML(s.description)',self.compiler)
        self.assertIn('escapeHTML(s.action)',self.compiler)
        self.assertIn('navigator.clipboard.writeText',self.js)
        self.assertIn('STATIC_CHECKS_PASSED_MANUAL_AUDIT_REQUIRED',self.quality)
        self.assertIn('BLOCKED_STATIC_CONTRAST',self.quality)


if __name__=="__main__":
    unittest.main()
