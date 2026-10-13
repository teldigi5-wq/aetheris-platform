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
                           "spec-device-desktop","spec-device-mobile","spec-copy","spec-reset",
                           "spec-undo","spec-redo","spec-status",
                           "spec-snapshot-input","spec-snapshot-copy","spec-snapshot-import","spec-handoff-status"):
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



    def test_design_history_does_not_persist_and_editor_copy_is_legible(self):
        self.assertIn("HISTORY_LIMIT=24",self.js)
        self.assertIn('refs["spec-undo"].addEventListener("click"',self.js)
        self.assertIn('refs["spec-redo"].addEventListener("click"',self.js)
        self.assertIn(".spec-controls label { display:grid; align-content:start; gap:8px; font-size:14px;",self.styles)
        self.assertIn("font-size:16px; min-height:44px; line-height:1.45;",self.styles)

    def test_snapshot_handoff_remains_manually_approved_and_inert(self):
        c=self.contract
        self.assertEqual(1,c["portableSnapshotSchemaVersion"])
        self.assertEqual(2048,c["portableSnapshotMaxChars"])
        self.assertIs(c["portableSnapshotManualTransferOnly"],True)
        for name in ("snapshotNetworkSyncEnabled","snapshotScriptExecutionEnabled","snapshotPersistenceEnabled"):
            self.assertIs(c[name],False,name)
        self.assertIn('maxlength="2048"',self.html)
        self.assertIn('type="button" class="ghost-button">Copy concept JSON',self.html)
        self.assertIn('type="button" class="ghost-button">Import pasted JSON',self.html)
        self.assertIn('engine.parseSnapshotJSON(refs["spec-snapshot-input"].value)',self.js)
        self.assertIn('engine.snapshotJSON(candidate)',self.js)
        self.assertIn('snapshotJSON,parseSnapshotJSON',self.compiler)
        self.assertIn("SNAPSHOT_MAX_CHARS=2048",self.compiler)
        self.assertIn("Object.keys(data.spec).length!==required.length",self.compiler)
        self.assertIn("clipboard.writeText(text)",self.js)

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
