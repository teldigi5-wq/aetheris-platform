"""B62-G12: restore provenance and non-overridable NO_GO release guard."""
from __future__ import annotations
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("g12_gate",
          ROOT/"scripts/b62g12_release_readiness.py")
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)

PROOF = (ROOT/"tests/security/b62g12-synthetic-restore-proof.sh").read_text()
OVERLAY = (ROOT/"tests/security/b62g12-restore-overlay.yml").read_text()
WORKFLOW = (ROOT/".github/workflows/b62g12-revocation-restore-readiness.yml").read_text()
DOC = (ROOT/"docs/security/B62G12_RESTORE_AND_CUTOVER_NO_GO_20261027.md").read_text()

def sample():
    p={key: False for key in gate.BOOL_KEYS}
    p.update({"contract_version":1, "scope":"B62G12_DISPOSABLE_CI_ONLY"})
    for key in gate.SYNTHETIC:
        p[key]=True
    return p

class B62G12ReleaseSafety(unittest.TestCase):
    def test_complete_synthetic_evidence_cannot_authorize_production(self):
        self.assertEqual(gate.classify(sample()),
            "NO_GO_REQUIRES_INDEPENDENT_OWNER_AND_RELEASE_APPROVAL")
        for name in gate.PRODUCTION:
            with self.subTest(name=name):
                p=sample()
                p[name]=True
                self.assertEqual(gate.classify(p),
                    "NO_GO_UNVERIFIED_PRODUCTION_ATTESTATION")
        for name in gate.SYNTHETIC:
            with self.subTest(missing=name):
                p=sample()
                p[name]=False
                self.assertEqual(gate.classify(p),"NO_GO_SYNTHETIC_PROOF_INCOMPLETE")

    def test_malformed_untrusted_unexpected_evidence_denied(self):
        for payload in (None, [], {}, "GO", 1, True):
            self.assertEqual(gate.classify(payload),
                "NO_GO_INVALID_OR_UNTRUSTED_EVIDENCE")
        for key, value in [
            ("contract_version", True),("contract_version", 2),
            ("scope","PRODUCTION"),("wrong_password_rejected","true"),
        ]:
            p=sample()
            p[key]=value
            self.assertEqual(gate.classify(p),
                "NO_GO_INVALID_OR_UNTRUSTED_EVIDENCE")
        p=sample()
        p["production_release_approved"]=True
        self.assertEqual(gate.classify(p),
            "NO_GO_INVALID_OR_UNTRUSTED_EVIDENCE")

    def test_process_exit_code_is_never_release_admission(self):
        with tempfile.TemporaryDirectory() as tmp:
            filename=Path(tmp)/"synthetic.json"
            filename.write_text(json.dumps(sample()),encoding="utf-8")
            proc=subprocess.run([sys.executable,
                str(ROOT/"scripts/b62g12_release_readiness.py"),str(filename)],
                text=True,capture_output=True,check=False,timeout=4)
            self.assertEqual(proc.returncode,1)
            self.assertEqual(proc.stdout.strip(),
                "B62G12_NO_GO_REQUIRES_INDEPENDENT_OWNER_AND_RELEASE_APPROVAL")
            filename.write_text("x"*4097,encoding="utf-8")
            oversized=subprocess.run([sys.executable,
                str(ROOT/"scripts/b62g12_release_readiness.py"),str(filename)],
                text=True,capture_output=True,check=False,timeout=4)
            self.assertNotEqual(oversized.returncode,0)
            self.assertIn("NO_GO",oversized.stdout)
            bad=subprocess.run([sys.executable,
                str(ROOT/"scripts/b62g12_release_readiness.py")],
                text=True,capture_output=True,check=False,timeout=4)
            self.assertEqual(bad.returncode,2)
        self.assertIn("return 1  # No successful release-admission", 
                      (ROOT/"scripts/b62g12_release_readiness.py").read_text())

    def test_true_password_auth_separate_client_and_backup_staleness(self):
        for mark in ('set(d["services"])=={"postgres","auth-probe"}',
            "not service.get(\"ports\")", "not any(v.get(\"external\")",
            "auth-probe timeout 7s psql", "-h postgres",
            "PGCONNECT_TIMEOUT=2", "pg_dump -U b62g_synthetic",
            "pg_restore -U b62g_synthetic", "--exit-on-error --no-owner",
            "b62g12-stale.dump", "b62g12-latest.dump",
            "REVOKE CONNECT ON DATABASE $db FROM PUBLIC;",
            "b62g12_stale", "b62g12_latest", "key-current", "key-safe",
            "B62G12_STALE_PRE_REVOCATION_RESTORE_UNSAFE_ALLOW_REJECTED_BY_POLICY",
            "B62G12_RESTORED_DB_EMERGENCY_WRITER_UNAUTHORIZED",
            "B62G12_WRONG_PASSWORD_ACCEPTED",
            "B62G12_READ_ONLY_ROLE_UPDATED_REVOCATION_AFTER_RESTORE",
            "trap cleanup EXIT", "down --volumes --remove-orphans"):
            self.assertIn(mark,PROOF)
        self.assertIn("networks: [isolated]",OVERLAY)
        self.assertIn('cap_drop: ["ALL"]',OVERLAY)
        self.assertNotIn("ports:",OVERLAY)
        self.assertNotIn("docker.sock",OVERLAY)
        self.assertNotIn("SET ROLE",PROOF)
        self.assertNotIn("postgresql://localhost",PROOF)

    def test_exact_commit_regression_and_release_governance(self):
        for mark in ("github.event.pull_request.head.sha",
              "persist-credentials: false", "mvnw -B -ntp -f pom.xml test",
              "bash tests/security/b62g12-synthetic-restore-proof.sh",
              "if: always()", "b62g12_release_readiness.py"):
            self.assertIn(mark,WORKFLOW)
        for mark in ("NOT INTEGRATED","NO_GO","production",
              "historical migration","owner approval","Flyway",
              "Gateway","User","Audit","read-after-write",
              "wrong-password", "old restore", "certificate"):
            self.assertIn(mark,DOC)

if __name__=="__main__":
    unittest.main()
