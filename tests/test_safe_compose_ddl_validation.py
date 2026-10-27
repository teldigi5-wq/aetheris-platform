"""Fail-closed future migration configuration: source and rendered Compose."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import unittest

ROOT=Path(__file__).resolve().parents[1]
NAMES=("docker-compose.yml","docker-compose.core.yml",
       "docker-compose.integration-external.yml")

class ReviewOnlyNoAutomaticDdl(unittest.TestCase):
    def test_all_auto_mutating_compose_defaults_are_gone(self):
        for name in NAMES:
            data=(ROOT/name).read_text()
            self.assertEqual(data.count("SPRING_JPA_HIBERNATE_DDL_AUTO: update"),0)
            self.assertEqual(data.count("SPRING_JPA_HIBERNATE_DDL_AUTO: validate"),2)
            self.assertNotIn("SPRING_JPA_HIBERNATE_DDL_AUTO: create",data)

    def test_automatic_schema_mutations_denied_in_rendered_compose(self):
        if not shutil.which("docker"):
            self.skipTest("Docker CLI absent; static check still applies")
        for name in NAMES:
            content=(ROOT/name).read_text()
            env=os.environ.copy()
            for variable in re.findall(r"\$\{([A-Z_][A-Z_0-9]*):\?",content):
                env[variable]="CI_REVIEW_ONLY_EPHEMERAL_PLACEHOLDER_"+variable
            result=subprocess.run(["docker","compose","-f",name,"config","--format","json"],
                cwd=ROOT,env=env,capture_output=True,text=True,timeout=30)
            # Don't log rendered configs; they can include synthetic secrets.
            self.assertEqual(result.returncode,0,
                "Compose config invalid for "+name+" (redacted)")
            data=json.loads(result.stdout)
            targets=[data["services"][x] for x in ("identity-service","user-service")]
            for svc in targets:
                self.assertEqual(svc["environment"]["SPRING_JPA_HIBERNATE_DDL_AUTO"],
                                 "validate")

    def test_independent_release_authorization_still_no_go(self):
        import sys
        sys.path.insert(0,str(ROOT/"scripts"))
        import security_closure_preflight as closure
        result=closure.evaluate(ROOT)
        self.assertEqual(result["status"],"NO_GO")
        self.assertFalse(result["safe_to_merge_or_deploy"])
        self.assertEqual(result["unreviewed_schema_mutation"],[])
        self.assertEqual(result["backend_service_authentication_gaps"],[])
        self.assertEqual(len(result["independent_release_attestations_required"]),9)

if __name__=="__main__":
    unittest.main()
