"""B62-G7 source guard for coordinated test-only HTTP authority.

Requires actual Maven/JUnit execution in CI; never substitutes mocked outputs.
"""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
SERVER=(ROOT/"tests/security/b62g7-loopback-authority.py").read_text()
RUNNER=(ROOT/"tests/security/b62g7-shared-revocation-proof.sh").read_text()
CI=(ROOT/".github/workflows/b62g7-shared-loopback-revocation.yml").read_text()
FILES={
  "gateway":ROOT/"gateway/src/test/java/io/aetheris/gateway/B62G7SharedHttpContractIT.java",
  "user":ROOT/"user-service/src/test/java/io/aetheris/users/security/B62G7SharedHttpContractIT.java",
  "audit":ROOT/"audit-service/src/test/java/io/aetheris/audit/security/B62G7SharedHttpContractIT.java",
}

class B62G7SourceContract(unittest.TestCase):
    def test_fixture_cannot_publish_network_or_accept_admin_mutations(self):
        for marker in ('"127.0.0.1", PINNED_PORT', 'PINNED_PORT = 15377',
                       'choices=("baseline", "epoch", "blocked")',
                       '"B62G7_TEST_ONLY_READY"', 'int(iat) > CUTOFF',
                       'self.stage == "blocked"', 'Cache-Control', 'no-store'):
            self.assertIn(marker, SERVER)
        self.assertNotIn("0.0.0.0", SERVER)
        self.assertNotIn("def do_POST", SERVER)
        self.assertNotIn("postgresql://", SERVER)
        self.assertNotIn("PRIVATE KEY", SERVER)

    def test_three_real_filters_use_same_test_server_and_same_signed_subject(self):
        for module,path in FILES.items():
            src=path.read_text()
            self.assertIn("class B62G7SharedHttpContractIT",src)
            self.assertIn('KID = "b62g7-synthetic-current"',src)
            self.assertIn("Instant.parse(\"2026-10-27T09:00:00Z\")",src)
            self.assertIn("generator.initialize(3072)" if module == "gateway"
                          else "g.initialize(3072)",src)
            self.assertIn("Jwts.SIG.RS256",src)
            self.assertIn("127.0.0.1:15377/decision",src)
            self.assertIn("response.statusCode() == 200",src)
            self.assertIn('"ALLOW".equals(response.body())',src)
            self.assertIn('"baseline", "epoch", "blocked", "outage"',src)
            if module == "gateway":
                self.assertIn("Invalid signature cannot",src)
            else:
                self.assertIn("Invalid JWT cannot consult revocation authority",src)
            self.assertNotIn("0.0.0.0",src)
            self.assertNotIn("System.getenv(\"REVOCATION_URL\")",src)
            if module == "gateway":
                self.assertIn("subscribeOn(Schedulers.boundedElastic())",src)
                self.assertIn("MockServerWebExchange",src)
            else:
                self.assertIn(".addFilters(filter).build()",src)

    def test_exact_sha_comprehensive_maven_and_true_outage(self):
        for token in ('trap cleanup EXIT', 'kill "$AUTHORITY_PID"',
                      'python3 -u tests/security/b62g7-loopback-authority.py',
                      'for phase in baseline epoch blocked', 'run_phase outage',
                      'B62G7SharedHttpContractIT', 'mvnw -B -ntp -f pom.xml'):
            self.assertIn(token,RUNNER)
        for token in ('github.event.pull_request.head.sha',
                      'persist-credentials: false',
                      'mvnw -B -ntp -f pom.xml test',
                      'bash tests/security/b62g7-shared-revocation-proof.sh'):
            self.assertIn(token,CI)

    def test_no_release_or_production_claim(self):
        doc=(ROOT/"docs/security/B62G7_SHARED_HTTP_CONTRACT_20261027.md").read_text()
        for token in ("NOT INTEGRATED","NO_GO","production","Gateway","User",
                      "Audit","owner approval","database migration",
                      "PostgreSQL","read-after-write","certificate"):
            self.assertIn(token,doc)

if __name__=="__main__":
    unittest.main()
