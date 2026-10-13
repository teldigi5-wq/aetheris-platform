"""Security proof for native, untrusted mission-event SSE parser."""
import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CODEC=ROOT/"apps"/"syntra-desktop"/"mission-stream"
DESKTOP=ROOT/"apps"/"syntra-desktop"
class NativeMissionStreamContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest=(CODEC/"Cargo.toml").read_text()
        cls.source=(CODEC/"src"/"lib.rs").read_text()
        cls.native=(DESKTOP/"src-tauri"/"src"/"main.rs").read_text()
        cls.desktop_manifest=(DESKTOP/"src-tauri"/"Cargo.toml").read_text()
        cls.info=json.loads((DESKTOP/"mission-evidence.contract.json").read_text())
    def test_parser_isolated_from_live_credentials_renderer_and_network(self):
        self.assertNotIn("mission-stream",self.desktop_manifest)
        self.assertNotIn("syntra_native_mission_stream",self.native)
        for forbidden in ["reqwest","hyper::","tokio::net","TcpStream","WebSocket",
                          "std::process::Command","std::fs::write","Authorization",
                          "CredReadW","__TAURI__","localStorage","println!(", "eprintln!("]:
            self.assertNotIn(forbidden,self.source)
        self.assertIn("UnauthenticatedBytes",self.source)
        self.assertIn("Does NOT fetch, authenticate, persist, connect to Tauri, or execute work.",self.source)
        self.assertIs(self.info["nativeAuthenticatedStreamConnected"],False)
        self.assertIs(self.info["eventsAreAuthenticated"],False)
    def test_parser_is_bounded_and_source_schema_explicit(self):
        for needed in ["MAX_FRAME_BYTES: usize = 16 * 1024",
                       "MAX_BUFFER_BYTES: usize = 32 * 1024",
                       "MAX_SEEN_IDS: usize = 1024","TaskEventDecoder",
                       "BorrowedTaskEvent","serde_json::from_slice",
                       '"taskId"', '"agentId"', '"task-event"',
                       '"COMPLETED"','"ROLLING_BACK"','"FAILED"',
                       "self.seen.contains(&id)","self.seen_order.len() > MAX_SEEN_IDS"]:
            self.assertIn(needed,self.source)
        self.assertIn('serde = { version = "=1.0.228"',self.manifest)
        self.assertIn('serde_json = "=1.0.145"',self.manifest)
    def test_sensitive_fields_ignored_and_source_pinned(self):
        self.assertIn("Deliberately do not deserialize",self.source)
        self.assertNotIn("pub message:",self.source)
        self.assertNotIn("pub metadata:",self.source)
        self.assertEqual("65a6262717adcd52ac8d8a16ed6f223e299fd74d",self.info["sourceCommit"])
        self.assertEqual("GET /api/orchestrator/live/events",self.info["streamRoute"])
if __name__=="__main__":
    unittest.main()
