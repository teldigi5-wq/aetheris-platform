"""B62-G4 exact-source safety guard for disposable Java JDBC acceptance."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = (ROOT/"identity-service/src/test/java/io/aetheris/identity/B62G4RealJdbcRevocationIT.java").read_text()
SCRIPT = (ROOT/"tests/security/b62g4-java-jdbc-proof.sh").read_text()
OVERLAY = (ROOT/"tests/security/docker-compose.b62g4-local-jdbc-overlay.yml").read_text()
WORKFLOW = (ROOT/".github/workflows/b62g4-real-jdbc-revocation.yml").read_text()
DOC = (ROOT/"docs/security/B62G4_JAVA_JDBC_REVOCATION_20261027.md").read_text()

class B62G4Boundaries(unittest.TestCase):
    def test_jjwt_before_real_jdbc_and_bounded_prepared_query(self):
        for part in (
            "CI-ONLY integration test", "generator.initialize(3072)",
            ".verifyWith(pinnedPublicKey)", ".parseSignedClaims(token)",
            '!"RS256".equals(signed.getHeader().getAlgorithm())',
            "Set.of(AUDIENCE).equals(claims.getAudience())",
            "return decision.allowed(uid, ISSUER, kid,",
            "DriverManager.getConnection(", "connection.setReadOnly(true)",
            "connection.setAutoCommit(false)",
            "SET LOCAL ROLE b62g_verifier",
            "PreparedStatement query = connection.prepareStatement(",
            "synthetic_post_signature_allowed(?,?,?,?)",
            "query.setQueryTimeout(2)",
            "connectTimeout=2&socketTimeout=3&sslmode=disable",
            "catch (SQLException orNetworkFailure)",
            "B62G4_",
        ):
            self.assertIn(part, JAVA)
        self.assertLess(JAVA.index(".parseSignedClaims(token)"),
                        JAVA.index("return decision.allowed(uid, ISSUER, kid,"))
        self.assertNotIn("@Service", JAVA)
        self.assertNotIn("@RestController", JAVA)
        self.assertNotIn("shell=True", JAVA)
        self.assertNotIn("Statement.executeQuery(", JAVA)

    def test_compose_port_is_localhost_only_and_disposable(self):
        self.assertIn("java-proof:", OVERLAY)
        self.assertIn("target: build", OVERLAY)
        self.assertIn("networks: [isolated]", OVERLAY)
        self.assertIn('cap_drop: ["ALL"]', OVERLAY)
        self.assertIn('security_opt: ["no-new-privileges:true"]', OVERLAY)
        self.assertNotIn("ports:", OVERLAY)
        self.assertIn("B62G4_ZERO_HOST_PORT_INTERNAL_JAVA_DB_TOPOLOGY", SCRIPT)
        for fragment in ("trap cleanup EXIT", "down --volumes --remove-orphans",
                         "docker compose", "b62g-revocation-prototype.sql",
                         "b62g2-readonly-role.sql",
                         "run_java online", "run_java restarted",
                         "run_java outage", "stop postgres"):
            self.assertIn(fragment, SCRIPT)

    def test_ci_exact_sha_and_no_live_service_integration(self):
        for fragment in ("github.event.pull_request.head.sha",
                         "persist-credentials: false",
                         "mvnw -B -ntp -f pom.xml test",
                         "bash tests/security/b62g4-java-jdbc-proof.sh",
                         "if: always()"):
            self.assertIn(fragment, WORKFLOW)
        for word in ("NOT INTEGRATED","NO_GO", "production",
                     "historical migration", "owner approval",
                     "Gateway", "User", "Audit", "JDBC", "read-only",
                     "certificate"):
            self.assertIn(word,DOC)

if __name__ == "__main__":
    unittest.main()
