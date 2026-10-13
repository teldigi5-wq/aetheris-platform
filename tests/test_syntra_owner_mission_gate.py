"""Fail-close assertions for the source-audited mission event owner boundary."""
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"apps"/"syntra-desktop"
GATE=APP/"mission-owner-gate"

class OwnerMissionBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract=json.loads((GATE/"owner-isolation.contract.json").read_text())
        cls.rust=(GATE/"src"/"lib.rs").read_text()
        cls.cargo=(GATE/"Cargo.toml").read_text()
        cls.desktop=(APP/"src-tauri"/"Cargo.toml").read_text()
        cls.main=(APP/"src-tauri"/"src"/"main.rs").read_text()
    def test_backend_owner_isolation_not_falsely_claimed(self):
        evidence=self.contract["evidence"]
        self.assertTrue(evidence["aggregateSseUsesSubscribeAll"])
        for field in ("taskEntityHasVerifiedOwnerBinding","taskSseEndpointChecksOwnerScope",
                      "gatewayBearerReadScopeAloneIsOwnerBinding"):
            self.assertFalse(evidence[field],field)
        self.assertEqual("BLOCKED_OWNER_BOUND_TASK_ISOLATION",self.contract["status"])
        self.assertEqual("BLOCKED_PENDING_HARDWARE",self.contract["physicalPcStatus"])
        self.assertEqual("65a6262717adcd52ac8d8a16ed6f223e299fd74d",self.contract["sourceRuntime"]["sha"])
    def test_no_production_api_can_construct_owner_proof(self):
        self.assertIn("#[cfg(test)]\n    fn synthetic_owner_grant",self.rust)
        self.assertIn("Self { grant: None, stream: TaskEventDecoder::new(), revoked: false }",self.rust)
        self.assertIn("pub fn allow_live_events(&self) -> bool",self.rust)
        self.assertIn("false\n    }",self.rust)
        self.assertNotIn("mission-owner-gate",self.desktop)
        self.assertNotIn("syntra_owner_mission_gate",self.main)
        for k in ("globalSseAllowed","perTaskSseAllowed","backendOwnerProofAvailable",
                  "nativeAuthenticatedSessionConnected","desktopTauriCommandExposed",
                  "productionDataAccessAllowed","modelOrToolExecutionAllowed",
                  "testOwnerGrantsCompiledInProduction"):
            self.assertIs(self.contract["activation"][k],False,k)
    def test_streams_are_filtered_and_revoke_on_foreign_events(self):
        for marker in ("GlobalFeedForbidden","TaskNotAuthorized","RuntimeOwnerMappingMissing",
                       "SessionExpired","SessionRevoked","MAX_OWNER_TASKS: usize = 24",
                       "self.revoke();","pub fn route(","pub fn ingest(",
                       "synthetic_owner_grant","UnauthenticatedBytes"):
            self.assertIn(marker,self.rust)
        for forbidden in ("reqwest","TcpStream","WebSocket","Authorization","Bearer ",
                          "std::process::Command", "CredReadW", "fetch(","localStorage",
                          "tracing::","eprintln!("):
            self.assertNotIn(forbidden,self.rust)
        self.assertIn('syntra-native-mission-stream = { path = "../mission-stream" }',self.cargo)

if __name__=="__main__":
    unittest.main()
