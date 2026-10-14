"""Review-only non-activation guarantees for Syntra's new native SSE client.

These are static safety invariants, not proof of a signed production session.
"""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
CLIENT = ROOT / "apps/syntra-desktop/checkpoint-sse/src/lib.rs"
APP = ROOT / "apps/syntra-desktop/src-tauri/src/main.rs"
OWNER_GATE = ROOT / "apps/syntra-desktop/mission-owner-gate/src/lib.rs"


class NativeCheckpointBoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = CLIENT.read_text(encoding="utf-8")
        cls.app = APP.read_text(encoding="utf-8")
        cls.gate = OWNER_GATE.read_text(encoding="utf-8")

    def test_no_tauri_or_ui_network_wiring_or_javascript_token_delivery(self):
        self.assertNotIn("checkpoint_sse", self.app)
        self.assertNotIn("checkpoint-sse", self.app)
        self.assertNotIn("NativeAccessToken", self.app)
        self.assertNotIn("#[tauri::command]", self.client)
        self.assertNotIn("tauri::", self.client)
        self.assertNotIn("window.emit", self.client)
        self.assertNotIn("localStorage", self.client)

    def test_token_and_verified_session_have_no_public_constructors(self):
        self.assertIn("pub struct NativeAccessToken(Zeroizing<String>);", self.client)
        self.assertIn("pub struct VerifiedNativeSession {", self.client)
        self.assertNotRegex(self.client, r"pub fn (?:new|issue|mint|from_js|from_token)\s*\(")
        self.assertIn("fn test_token()", self.client)
        self.assertIn("fn test_session()", self.client)
        self.assertIn("#[cfg(test)]", self.client)
        self.assertIn("NativeAccessToken([REDACTED])", self.client)

    def test_only_fixed_loopback_with_no_redirect_or_proxy(self):
        self.assertIn("http://127.0.0.1:", self.client)
        self.assertIn(".no_proxy()", self.client)
        self.assertIn(".redirect(Policy::none())", self.client)
        self.assertIn("Authorization", self.client) if "Authorization" in self.client else self.assertIn("AUTHORIZATION", self.client)
        self.assertNotIn("https://example", self.client)
        self.assertNotIn("Last-Event-ID", self.client.split("#[cfg(test)]")[0])

    def test_old_unfiltered_global_feed_remains_inaccessible(self):
        self.assertIn("/api/orchestrator/live/events", self.client)
        self.assertIn("false\n    }", self.gate)
        self.assertIn("BLOCKED_PENDING_HARDWARE", self.client)
        self.assertIn("BLOCKED_NATIVE_AUTHENTICATED_SESSION_NOT_WIRED", self.client)
        self.assertIn("task-recorded", self.client)
        self.assertIn("PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME", self.client)
        self.assertNotIn('self.client.get(LEGACY_GLOBAL_ROUTE)', self.client)


if __name__ == "__main__":
    unittest.main()
