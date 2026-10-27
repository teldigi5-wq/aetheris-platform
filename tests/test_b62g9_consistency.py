"""B62-G9: structural and local logic guards for no-stale-fallback proof."""
from pathlib import Path
import importlib.util
import subprocess
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"tests/security"
PY=(BASE/"b62g9-primary-consistency-client.py").read_text()
SCRIPT=(BASE/"b62g9-stale-consistency-proof.sh").read_text()
OVERLAY=(BASE/"b62g9-stale-snapshot-overlay.yml").read_text()
WF=(ROOT/".github/workflows/b62g9-stale-primary-consistency.yml").read_text()
DOC=(ROOT/"docs/security/B62G9_STALE_PRIMARY_AUTHORITY_PROOF_20261027.md").read_text()

spec=importlib.util.spec_from_file_location("b62g9_client",
    BASE/"b62g9-primary-consistency-client.py")
client=importlib.util.module_from_spec(spec)
spec.loader.exec_module(client)

class B62G9ConsistencyGuard(unittest.TestCase):
    def test_primary_decision_never_calls_stale_copy(self):
        self.assertEqual(client.COMPOSE[0:2],("docker","compose"))
        self.assertEqual(len(client.READERS),3)
        self.assertEqual(client.primary_authoritative.__doc__.strip(),
                         "Never queries stale-snapshot. ERROR is DENY; no cached allow.")
        with patch.object(client, "raw_read", return_value="ERROR") as reader:
            self.assertEqual(client.primary_authoritative("b62g8_gateway_reader",42,11000),"DENY")
            reader.assert_called_once_with("b62g8_gateway_reader",42,11000)
        with patch.object(client, "raw_read", return_value="DENY"):
            self.assertEqual(client.primary_authoritative("b62g8_user_reader",42,11000),"DENY")
        with patch.object(client, "raw_read", return_value="ALLOW"):
            self.assertEqual(client.primary_authoritative("b62g8_audit_reader",43,14001),"ALLOW")

    def test_timeout_credential_rejection_and_bounded_reader_network(self):
        with patch.object(client.subprocess,"run",side_effect=subprocess.TimeoutExpired(["docker"],1)):
            self.assertEqual(client.raw_read("b62g8_user_reader",42,11000),"ERROR")
            self.assertEqual(client.primary_authoritative("b62g8_user_reader",42,11000),"DENY")
        self.assertEqual(client.raw_read("unknown_role",42,11000),"ERROR")
        self.assertEqual(client.raw_read("b62g8_user_reader",42.5,11000),"ERROR")
        self.assertEqual(client.raw_read("b62g8_user_reader",42,12000),"ERROR")
        self.assertTrue(0 < client.TIMEOUT_SECONDS <= 10)
        self.assertIn("if result.returncode != 0",PY)
        self.assertIn('"t": "ALLOW", "f": "DENY"',PY)
        self.assertIn("with ThreadPoolExecutor(max_workers=3)",PY)
        self.assertIn("snapshot=True",PY)

    def test_dual_disposable_db_topology_has_no_published_ports(self):
        self.assertIn("stale-snapshot:",OVERLAY)
        self.assertIn("auth-probe:",OVERLAY)
        self.assertIn('entrypoint: ["sleep", "3600"]',OVERLAY)
        self.assertIn('"auth-probe", "psql"',PY)
        self.assertIn('"-h", service',PY)
        self.assertIn('auth-probe timeout 10s psql',SCRIPT)
        self.assertIn("b62g9_stale_snapshot_data",OVERLAY)
        self.assertIn("networks: [isolated]",OVERLAY)
        self.assertNotIn("ports:",OVERLAY)
        self.assertNotIn("external:",OVERLAY)
        for marker in ("set -euo pipefail","trap cleanup EXIT",
                       "down --volumes --remove-orphans",
                       'set(d["services"]) == {"postgres","stale-snapshot","auth-probe"}',
                       'not svc.get("ports")',
                       "client baseline","client postcommit",
                       "client blocked","client restarted","client outage",
                       "restart postgres","stop postgres",
                       "B62G9_STALE_READ_DENY_NO_FALLBACK_SYNTHETIC_CONSISTENCY_PASS"):
            self.assertIn(marker,SCRIPT)

    def test_exact_ci_and_honest_replication_limitations(self):
        for marker in ("github.event.pull_request.head.sha",
                       "persist-credentials: false",
                       "mvnw -B -ntp -f pom.xml test",
                       "bash tests/security/b62g9-stale-consistency-proof.sh",
                       "if: always()"):
            self.assertIn(marker,WF)
        for marker in ("NOT INTEGRATED","NO_GO","owner approval",
                       "historical migration","production","Gateway","User",
                       "Audit","stale replicas","read-after-write",
                       "independent","NOT a streaming replica","certificate"):
            self.assertIn(marker,DOC)

if __name__=="__main__":
    unittest.main()
