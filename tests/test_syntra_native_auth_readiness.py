"""Fail-closed native session-readiness integration assertions; no owner-PC claims."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"apps"/"syntra-desktop"
RUST=APP/"src-tauri"/"src"

class NativeAuthReadinessContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy=(RUST/"auth_readiness.rs").read_text(encoding="utf-8")
        cls.native=(RUST/"main.rs").read_text(encoding="utf-8")
        cls.html=(APP/"ui"/"index.html").read_text(encoding="utf-8")
        cls.ui=(APP/"ui"/"auth-readiness-panel.js").read_text(encoding="utf-8")
        cls.desktop=(APP/"desktop-shell.contract.json").read_text(encoding="utf-8")

    def test_no_frontend_supplied_native_trust_flags_or_credentials(self):
        self.assertIn("fn syntra_auth_readiness() -> NativeAuthReadiness",self.native)
        self.assertIn("let gate = auth_readiness::pre_pc_report()",self.native)
        self.assertIn("credential_vault_connected: false",self.native)
        self.assertIn("authenticated_session_active: false",self.native)
        self.assertIn("can_execute: false",self.native)
        self.assertIn("tauri::generate_handler![probe_aetheris_runtime, export_diagnostics, syntra_auth_readiness]",self.native)
        self.assertIn('invoke("syntra_auth_readiness")',self.ui)
        self.assertNotIn('invoke("auth_login"',self.ui)
        self.assertNotIn("window.__TAURI__?.core?.invoke(",self.policy)
        self.assertIn("BLOCKED_PENDING_HARDWARE",self.desktop)

    def test_native_policy_is_os_secret_storage_and_consent_gated(self):
        for check in ("!e.os_vault_verified","!e.signed_session_verified","expires <= now_unix",
                      "!e.read_scope_verified","!e.gateway_auth_verified","!e.owner_consent_verified",
                      "!e.hardware_certified","TrustedEvidence::default()"):
            self.assertIn(check,self.policy)
        self.assertIn('BLOCKED_NATIVE_CREDENTIAL_BOUNDARY',self.policy)
        for forbidden in ("reqwest","http://","https://","Authorization","accessToken","refreshToken","CredWrite",
                          "localStorage","sessionStorage","std::fs::write","unsafe {"):
            self.assertNotIn(forbidden,self.policy)

    def test_accessible_panel_and_static_native_status(self):
        for marker in ('id="check-auth-readiness"','id="auth-readiness-value"',
                       'role="status"','aria-live="polite"','src="./auth-readiness-panel.js"'):
            self.assertIn(marker,self.html)
        self.assertLess(self.html.index('src="./app.js"'),self.html.index('src="./auth-readiness-panel.js"'))
        for forbidden in ("fetch(","localStorage","sessionStorage","innerHTML","outerHTML","eval(",
                          "accessToken","refreshToken","Authorization"):
            self.assertNotIn(forbidden,self.ui)

if __name__=="__main__":
    unittest.main()
