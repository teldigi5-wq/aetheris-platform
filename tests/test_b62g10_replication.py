"""B62-G10 physical streaming replica: independent executable contract guards."""
from pathlib import Path
import importlib.util
import subprocess
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
P=ROOT/"tests/security"
BOOT=(P/"b62g10-replica-bootstrap.sh").read_text()
OVERLAY=(P/"b62g10-physical-replica-overlay.yml").read_text()
PROOF=(P/"b62g10-physical-replication-proof.sh").read_text()
CLIENT=(P/"b62g10-physical-replication-client.py").read_text()
WF=(ROOT/".github/workflows/b62g10-physical-replication.yml").read_text()
DOC=(ROOT/"docs/security/B62G10_PHYSICAL_REPLICATION_20261027.md").read_text()
spec=importlib.util.spec_from_file_location("b62g10",P/"b62g10-physical-replication-client.py")
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class B62G10StructuralGuards(unittest.TestCase):
    def test_real_pg_basebackup_and_wal_stream_receiver(self):
        for marker in ("pg_basebackup","-X stream -R","standby.signal",
                       "primary_conninfo", "passfile=", "chmod 600",
                       "su-exec postgres postgres", "exec su-exec"):
            self.assertIn(marker,BOOT)
        self.assertNotIn("pg_dump",BOOT)
        self.assertIn("b62g10-replica-bootstrap.sh",OVERLAY)
        self.assertIn("b62g10_replica_volume",OVERLAY)
        self.assertIn('networks: [isolated]',OVERLAY)
        self.assertIn("auth-probe:",OVERLAY)
        self.assertIn('cap_drop: ["ALL"]',OVERLAY)
        self.assertNotIn("ports:",OVERLAY)

    def test_streaming_paused_wal_replay_and_read_only_recovery_proven(self):
        for phrase in (
            'set(d["services"]) == {"postgres","replica","auth-probe"}',
            "pg_stat_replication", "application_name='b62g10-physical-standby'",
            "SELECT pg_is_in_recovery();", "pg_wal_replay_pause()",
            "pg_get_wal_replay_pause_state()", "pg_wal_replay_resume()",
            "pg_last_wal_replay_lsn()", "pg_current_wal_lsn()",
            "client paused-epoch","client replayed-epoch",
            "client paused-kid","client replayed-kid",
            "stop postgres","client outage",
            "trap cleanup EXIT","down --volumes --remove-orphans"):
            self.assertIn(phrase,PROOF)
        self.assertNotIn("pg_promote",PROOF)
        self.assertNotIn("pg_ctl promote",PROOF)

    def test_reader_fails_closed_without_standby_fallback(self):
        self.assertEqual(len(module.READERS),3)
        self.assertIn("PGCONNECT_TIMEOUT=1",CLIENT)
        self.assertIn('"auth-probe"',CLIENT)
        self.assertIn('"-h", service',CLIENT)
        self.assertIn("response.returncode != 0",CLIENT)
        self.assertIn("subprocess.run",CLIENT)
        self.assertIn('"t": "ALLOW", "f": "DENY"',CLIENT)
        with patch.object(module,"raw_read",return_value="ERROR") as reader:
            self.assertEqual(module.authorize_primary("b62g8_gateway_reader",42,11000),"DENY")
            reader.assert_called_once_with("b62g8_gateway_reader",42,11000,kid="key-current")
        with patch.object(module,"raw_read",return_value="DENY"):
            self.assertEqual(module.authorize_primary("b62g8_user_reader",42,11000),"DENY")
        with patch.object(module,"raw_read",return_value="ALLOW"):
            self.assertEqual(module.authorize_primary("b62g8_audit_reader",43,14001),"ALLOW")
        self.assertEqual(module.raw_read("unknown",42,11000),"ERROR")
        self.assertEqual(module.raw_read("b62g8_user_reader",42.5,11000),"ERROR")

    def test_ci_and_certification_boundaries(self):
        for marker in ("github.event.pull_request.head.sha",
                       "persist-credentials: false",
                       "mvnw -B -ntp -f pom.xml test",
                       "bash tests/security/b62g10-physical-replication-proof.sh",
                       "if: always()"):
            self.assertIn(marker,WF)
        for marker in ("NOT INTEGRATED","NO_GO","production","physical streaming",
                       "pg_basebackup","WAL","lag","failover","replica",
                       "owner approval","historical migration",
                       "Gateway","User","Audit","certificate"):
            self.assertIn(marker,DOC)

if __name__=="__main__":
    unittest.main()
