"""Static non-execution contract tests for Syntra Media Studio staging."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps" / "syntra-desktop"


class MediaStudioContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = (APP / "ui" / "index.html").read_text(encoding="utf-8")
        cls.js = (APP / "ui" / "app.js").read_text(encoding="utf-8")
        cls.css = (APP / "ui" / "styles.css").read_text(encoding="utf-8")
        cls.contract = json.loads((APP / "media-studio.contract.json").read_text(encoding="utf-8"))

    def test_ui_is_accessible_and_advertises_draft_only(self):
        for marker in ('data-section="media"', 'data-view="media"', 'id="media-stage-form"',
                       'id="media-drafts"', 'id="media-stage-count"', 'id="media-status"'):
            self.assertIn(marker, self.html)
        self.assertIn("GENERATION NOT WIRED", self.html)
        self.assertIn("BLOCKED_PENDING_HARDWARE", self.html)
        self.assertIn("Ctrl+9", self.html)
        self.assertIn(".media-form", self.css)

    def test_staging_functions_never_invoke_runtime_or_network(self):
        staged = self.js.split("function stageMediaDraft(event) {", 1)[1].split("\nfunction clearMediaDrafts()", 1)[0]
        self.assertNotIn("fetch(", staged)
        self.assertNotIn("invoke(", staged)
        self.assertNotIn("localStorage.", staged)
        self.assertNotIn("navigator.", staged)
        self.assertIn("state.mediaDrafts.push", staged)
        self.assertIn("NOT SUBMITTED", self.js)
        self.assertIn("function clearMediaDrafts()", self.js)

    def test_physical_and_paid_provider_boundaries_are_unambiguous(self):
        c = self.contract
        self.assertEqual("BLOCKED_PENDING_HARDWARE", c["physicalPcStatus"])
        self.assertEqual("NOT_WIRED", c["imageGenerationFromDesktop"])
        self.assertEqual("NOT_WIRED", c["videoGenerationFromDesktop"])
        self.assertEqual("NOT_WIRED", c["desktopToRuntimeMediaAuthentication"])
        self.assertFalse(c["defaultPaidProviderEnabled"])
        self.assertFalse(c["externalNetworkAccessFromMediaView"])
        self.assertFalse(c["generatedMediaFilesClaimed"])
        self.assertTrue(c["noSyntheticSuccessClaims"])
        self.assertFalse(c["nativeDiagnosticsMayContainPrompts"])

    def test_native_diagnostics_stays_prompt_free(self):
        payload = self.js.split("function buildDiagnosticsPayload()", 1)[1].split("\nfunction renderDiagnosticsPreview()", 1)[0]
        self.assertNotIn("mediaDrafts", payload)
        self.assertNotIn("promptInput", payload)
        self.assertNotIn("media-prompt", payload)
        self.assertNotIn("media-negative", payload)


if __name__ == "__main__":
    unittest.main()
