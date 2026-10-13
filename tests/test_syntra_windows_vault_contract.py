"""Guard native-only WinCred integration: no renderer credentials or activation claims."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"apps"/"syntra-desktop"
VAULT=APP/"security-vault"

class WindowsCredentialVaultBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract=json.loads((VAULT/"vault-boundary.contract.json").read_text())
        cls.cargo=(VAULT/"Cargo.toml").read_text()
        cls.rust=(VAULT/"src"/"lib.rs").read_text()
        cls.main=(APP/"src-tauri"/"src"/"main.rs").read_text()
        cls.desktop_cargo=(APP/"src-tauri"/"Cargo.toml").read_text()
        cls.ui=(APP/"ui"/"app.js").read_text()
        cls.readiness=(APP/"ui"/"auth-readiness-panel.js").read_text()

    def test_production_vault_is_not_activated_or_exposed_to_renderer(self):
        c=self.contract
        self.assertEqual(1,c["schemaVersion"])
        self.assertEqual("ISOLATED_IMPLEMENTATION_NOT_WIRED",c["status"])
        self.assertEqual("BLOCKED_PENDING_HARDWARE",c["physicalPcStatus"])
        self.assertEqual("CRED_PERSIST_SESSION",c["persistence"])
        self.assertEqual(2560,c["maxSecretBytes"])
        self.assertEqual(["com.aetheris.syntra.auth.refresh.v1"],c["allowedProductionTargets"])
        for key in ("desktopTauriCommandExposed","liveLoginEnabled",
                    "realAccessOrRefreshTokenProvisioned","modelOrTaskExecutionEnabled",
                    "networkCallsEnabled","productionActivationAllowed",
                    "tokenLoggingAllowed","secretDebugPrintingAllowed"):
            self.assertIs(c[key],False,key)
        self.assertNotIn("security-vault",self.desktop_cargo)
        self.assertNotIn("syntra_credential_vault",self.main)
        self.assertNotIn("CredWriteW",self.main)
        self.assertNotIn("auth_login",self.main)
        self.assertNotIn("CredWriteW",self.ui)
        self.assertNotIn("CredReadW",self.readiness)

    def test_backend_uses_pinned_credential_manager_and_fixed_names(self):
        self.assertIn('windows-sys = { version = "=0.61.2"',self.cargo)
        self.assertIn('zeroize = "=1.8.2"',self.cargo)
        for required in ("CRED_TYPE_GENERIC", "CRED_PERSIST_SESSION",
                         "CredReadW", "CredWriteW", "CredDeleteW", "CredFree",
                         "MAX_SECRET_BYTES: usize = 2560", "impl Drop for Secret",
                         "self.0.zeroize()", "struct CredentialGuard", "impl Drop for CredentialGuard",
                         'com.aetheris.syntra.auth.refresh.v1', 'pub enum Slot'):
            self.assertIn(required,self.rust)
        for forbidden in ("CRED_PERSIST_ENTERPRISE","CRED_PERSIST_LOCAL_MACHINE",
                          "CredEnumerateW", "std::process::Command", "std::fs::write",
                          "println!(", "eprintln!(", "log::", "tracing::", "derive(Debug, Clone)",
                          "reqwest", "TcpStream", "Authorization: Bearer"):
            self.assertNotIn(forbidden,self.rust)

    def test_ci_smoke_uses_synthetic_only_slot_and_cleanup(self):
        self.assertIn("fn credential_manager_roundtrip_uses_disposable_ci_slot_only()",self.rust)
        self.assertIn('std::env::var("SYNTRA_TEST_CREDENTIAL_VAULT")',self.rust)
        self.assertIn('delete(Slot::SmokeTest)',self.rust)
        self.assertIn('struct Cleanup;',self.rust)
        self.assertIn('b"synthetic-ci-only-refresh"',self.rust)
        self.assertIn("pub fn pre_pc_report()", (APP/"src-tauri"/"src"/"auth_readiness.rs").read_text())

if __name__=="__main__":
    unittest.main()
