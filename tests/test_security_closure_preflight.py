"""Security release closure contract: no accidental green means production GO."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import security_closure_preflight as gate


class ClosureReleaseGate(unittest.TestCase):
    def test_source_remains_blocked_for_unapproved_dangerous_defaults(self):
        status = gate.evaluate(ROOT)
        self.assertEqual(status["status"], "NO_GO")
        self.assertFalse(status["safe_to_merge_or_deploy"])
        self.assertEqual(len(status["unreviewed_schema_mutation"]), 0)
        self.assertEqual(status["unreviewed_schema_mutation"], [])
        self.assertEqual(len(status["backend_service_authentication_gaps"]), 0)
        # Source presence is not authenticated workload identity or multi-tenant certification.
        self.assertEqual(len(status["independent_release_attestations_required"]), 9)

    def test_malformed_or_missing_files_must_never_return_release_go(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            result=gate.evaluate(root)
            self.assertEqual(result["status"],"NO_GO")
            self.assertEqual(len(result["unreviewed_schema_mutation"]),3)
            self.assertEqual(len(result["backend_service_authentication_gaps"]),2)
            for name in gate.COMPOSES:
                (root/name).write_text("services:\n  foo:\n    environment:\n      SPRING_JPA_HIBERNATE_DDL_AUTO: validate\n")
            for name in gate.BACKEND_FILTERS:
                path=root/name
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text("parseSignedClaims chain.doFilter\n")
            result=gate.evaluate(root)
            self.assertEqual(result["unreviewed_schema_mutation"],[])
            self.assertEqual(result["backend_service_authentication_gaps"],[])
            # Even a source-perfect, forged local 'approval' does NOT release.
            self.assertEqual(result["status"],"NO_GO")
            self.assertFalse(result["safe_to_merge_or_deploy"])
            self.assertEqual(len(result["independent_release_attestations_required"]),9)

    def test_require_ready_always_exits_nonzero(self):
        proc = subprocess.run([sys.executable,
            str(ROOT/"scripts/security_closure_preflight.py"),
            "--root",str(ROOT),"--require-ready"],
            capture_output=True,text=True,timeout=10)
        self.assertNotEqual(proc.returncode,0)
        data=json.loads(proc.stdout)
        self.assertEqual(data["status"],"NO_GO")
        self.assertFalse(data["safe_to_merge_or_deploy"])
        p = subprocess.run([sys.executable,
            str(ROOT/"scripts/security_closure_preflight.py"),
            "--root",str(ROOT),"--assert-blocked"],
            capture_output=True,text=True,timeout=10)
        self.assertEqual(p.returncode,0)
        self.assertIn('"status": "NO_GO"',p.stdout)

if __name__=="__main__":
    unittest.main()
