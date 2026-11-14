"""B62-G3: CI-only genuine JJWT signed-Java boundary, not production integration."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
TEST=ROOT/"identity-service/src/test/java/io/aetheris/identity/B62G3SignedJavaRevocationBoundaryTest.java"
WORKFLOW=ROOT/".github/workflows/b62g3-signed-java-revocation-boundary.yml"
DOC=ROOT/"docs/security/B62G3_SIGNED_JAVA_REVOCATION_BOUNDARY_20261027.md"

class B62G3StaticGuards(unittest.TestCase):
    def test_test_only_java_verifier_must_validate_real_signature_before_lookup(self):
        src=TEST.read_text()
        self.assertIn("package io.aetheris.identity;",src)
        self.assertIn("CI-ONLY proof",src)
        self.assertIn("generator.initialize(3072)",src)
        self.assertIn(".verifyWith(pinnedPublicKey)",src)
        self.assertIn(".clockSkewSeconds(0)",src)
        self.assertIn(".parseSignedClaims(token)",src)
        self.assertIn('!"RS256".equals(verified.getHeader().getAlgorithm())',src)
        self.assertIn("pinnedKid.equals(verified.getHeader().getKeyId())",src)
        self.assertIn("Set.of(AUDIENCE).equals(claims.getAudience())",src)
        self.assertIn("Duration.ofSeconds(3600)",src)
        self.assertIn("return reader.allowed(uid, ISSUER, pinnedKid,",src)
        self.assertLess(src.index(".parseSignedClaims(token)"),src.index("return reader.allowed(uid, ISSUER, pinnedKid,"))
        self.assertIn("catch (Exception invalidSignatureClaimsOrLookupFailure)",src)
        self.assertIn("java.util.concurrent.TimeoutException",src)
        self.assertIn("java.io.IOException",src)
        self.assertIn("dualAudience",src)
        self.assertIn('List.of("gateway-sim", "user-sim", "audit-sim")',src)
        self.assertNotIn("jdbc:",src)
        self.assertNotIn("@Service",src)
        self.assertNotIn("@RestController",src)

    def test_full_reactor_and_isolated_existing_postgres_proofs_preserved(self):
        src=WORKFLOW.read_text()
        self.assertIn("github.event.pull_request.head.sha",src)
        self.assertIn("persist-credentials: false",src)
        self.assertIn("mvnw -B -ntp -f pom.xml test",src)
        self.assertIn("B62G3SignedJavaRevocationBoundaryTest",src)
        self.assertIn("test_b62g2_adapter_unit.py",src)
        self.assertIn("test_b62g2_contract.py",src)
        self.assertIn("security/b62g2-readonly-revocation-adapter-20261026",src)
        self.assertNotIn("docker compose down",src)

    def test_real_filter_migration_outside_scope(self):
        text=DOC.read_text()
        for phrase in ("NOT INTEGRATED", "NO_GO", "read-only", "PostgreSQL",
                       "historical", "owner approval", "Gateway", "User", "Audit",
                       "3072-bit", "production", "eMudhra"):
            self.assertIn(phrase,text)
        self.assertNotIn("production-ready",text.lower())

if __name__=="__main__":
    unittest.main()
