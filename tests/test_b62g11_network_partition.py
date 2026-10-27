"""B62-G11: source and fail-closed guards for real Docker network faults."""
from pathlib import Path
import importlib.util
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
SEC=ROOT/"tests/security"
FAULT=(SEC/"b62g11-partition-proof.sh").read_text()
CLIENT=(SEC/"b62g11-partition-client.py").read_text()
RUNNER=(SEC/"b62g10-physical-replication-proof.sh").read_text()
WORKFLOW=(ROOT/".github/workflows/b62g11-real-network-partition.yml").read_text()
DOC=(ROOT/"docs/security/B62G11_NETWORK_PARTITION_RECOVERY_20261027.md").read_text()
spec=importlib.util.spec_from_file_location("g11",SEC/"b62g11-partition-client.py")
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class B62G11SecurityContract(unittest.TestCase):
    def test_inherits_real_physical_streaming_with_explicit_opt_in(self):
        self.assertIn('if [[ "${B62G11_TEST_ONLY_FAULTS:-0}" == "1" ]]',RUNNER)
        self.assertIn("source tests/security/b62g11-partition-proof.sh",RUNNER)
        self.assertIn("client replayed-epoch",RUNNER)
        self.assertIn('replica_sql "SELECT pg_wal_replay_pause();" >/dev/null',RUNNER)
        self.assertIn("B62G11_TEST_ONLY_FAULTS=1",WORKFLOW)
        self.assertIn("pg_basebackup", (SEC/"b62g10-replica-bootstrap.sh").read_text())

    def test_docker_network_partitions_are_real_and_isolated(self):
        for phrase in (
            'net.get("internal") is True', 'aetheris-b62g-epoch-proof_isolated',
            'docker network disconnect "$NET" "$replica_container"',
            'docker network connect --alias replica "$NET" "$replica_container"',
            'docker network disconnect "$NET" "$probe_container"',
            'docker network connect --alias auth-probe "$NET" "$probe_container"',
            'assert_network "$replica_container" disconnected',
            'assert_network "$probe_container" disconnected',
            'writer "SELECT b62g_proof.raise_principal_epoch(42,13000);"',
            'replica_sql "SELECT pg_wal_replay_pause();"',
            'replica_sql "SELECT pg_wal_replay_resume();"',
            'wait_replay_lsn "$wal"',
            'g11 replica-isolated', 'g11 replica-rejoined-paused',
            'g11 replica-caught-up', 'g11 client-isolated',
            'g11 client-recovered'):
            self.assertIn(phrase,FAULT)
        self.assertNotIn("pg_promote",FAULT)
        self.assertNotIn("network connect --alias postgres",FAULT)
        self.assertNotIn("iptables",FAULT)
        self.assertNotIn("docker.sock",FAULT)

    def test_primary_only_decision_and_timeout_are_proven_fail_closed(self):
        self.assertTrue(0 < module.MAX_SINGLE_AUTHORIZATION_SECONDS <= 10)
        self.assertIs(module.g10.authorize_primary.__module__ is not None,True)
        with patch.object(module.g10,"raw_read",return_value="ERROR") as reader:
            self.assertEqual(module.g10.authorize_primary("b62g8_gateway_reader",42,12001),"DENY")
            reader.assert_called_once_with("b62g8_gateway_reader",42,12001,kid="key-current")
        with patch.object(module.g10,"authorize_primary",return_value="DENY"):
            module.checked_primary("b62g8_user_reader",42,12001,"DENY")
            with self.assertRaises(AssertionError):
                module.checked_primary("b62g8_user_reader",42,12001,"ALLOW")
        with patch.object(module.g10,"raw_read",return_value="ERROR"):
            self.assertEqual(module.g10.authorize_primary("b62g8_audit_reader",43,14001),"DENY")
        self.assertIn('time.monotonic()',CLIENT)
        self.assertIn('g10.authorize_primary(role, uid, iat)',CLIENT)
        self.assertIn('g10.raw_read(role, 42, 12001, service="replica") != "ALLOW"',CLIENT)
        self.assertIn('g10.raw_read(role, 43, 14001) != "ERROR"',CLIENT)

    def test_ci_exact_revision_and_nonproduction_boundaries(self):
        for token in ("github.event.pull_request.head.sha",
                      "persist-credentials: false",
                      "mvnw -B -ntp -f pom.xml test",
                      "B62G11_TEST_ONLY_FAULTS=1",
                      "if: always()",
                      "b62g10-physical-replica-overlay.yml"):
            self.assertIn(token,WORKFLOW)
        for token in ("NOT INTEGRATED","NO_GO","production",
                      "network partition","Docker", "physical streaming",
                      "no automatic failover","owner approval","migration",
                      "Gateway","User","Audit","certificate","bounded"):
            self.assertIn(token,DOC)

if __name__=="__main__":
    unittest.main()
