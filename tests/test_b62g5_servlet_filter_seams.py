"""B62-G5 exact-source review gate: actual User/Audit servlet filter seams."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SERVICES=[
    "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
    "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java"
]
TESTS=[
    "user-service/src/test/java/io/aetheris/users/security/B62G5DirectRevocationServletTest.java",
    "audit-service/src/test/java/io/aetheris/audit/security/B62G5DirectRevocationServletTest.java"
]

class B62G5SafeServletIntegration(unittest.TestCase):
    def test_only_package_private_test_injection_and_default_disabled(self):
        for path in SERVICES:
            src=(ROOT/path).read_text()
            self.assertIn('private final TestOnlyRevocationDecision b62g5TestOnlyDecision;',src)
            self.assertIn('this(secret, issuer, audience, mode, publicKey, kid, previousPublicKey,',src)
            self.assertIn('previousKid, previousNotAfter, clock, null);',src)
            self.assertIn('DirectApiJwtFilter(String secret, String issuer, String audience, String mode,',src)
            self.assertIn('this.b62g5TestOnlyDecision = injectedDecision;',src)
            self.assertIn('if (injectedDecision != null && !"rs256".equals(mode))',src)
            self.assertIn('if (b62g5TestOnlyDecision != null) {',src)
            self.assertIn('verifiedSignerKid = signed.getHeader().getKeyId();',src)
            self.assertIn('claims.getIssuedAt().toInstant().getEpochSecond()',src)
            self.assertIn('catch (Exception storeUnavailableOrTimeout)',src)
            self.assertLess(src.index('boolean authorizedScope ='),src.index('if (b62g5TestOnlyDecision != null) {'))
            self.assertLess(src.index('if (b62g5TestOnlyDecision != null) {'),src.index('chain.doFilter(request, response);'))
            self.assertNotIn('jdbc:postgresql://',src)
            self.assertNotIn('@Bean',src)
            self.assertNotIn('aetheris.jwt.revocation-enabled',src)

    def test_actual_real_filter_http_tests_are_not_mocks_of_filter(self):
        for path in TESTS:
            src=(ROOT/path).read_text()
            for phrase in (
                'generator.initialize(3072)',
                '.addFilters(filter).build()',
                'Jwts.builder()',
                'Jwts.SIG.RS256',
                'status().isUnauthorized()',
                'status().isForbidden()',
                'status().isOk()',
                'assertEquals(0, calls.get()',
                'java.sql.SQLTimeoutException',
                'java.io.IOException',
                'void injectedRevocationCannotBeUsedInLegacyHmacMode()'
            ):
                self.assertIn(phrase,src)
            self.assertNotIn('mock(DirectApiJwtFilter.class)',src)

    def test_integrations_not_authorized_for_production(self):
        doc=(ROOT/"docs/security/B62G5_REAL_SERVLET_FILTER_SEAMS_20261027.md").read_text()
        for phrase in ("NOT INTEGRATED", "NO_GO", "Gateway", "User", "Audit",
                       "database migration", "owner approval", "production",
                       "test-only", "nonblocking", "read-after-write"):
            self.assertIn(phrase,doc)
        wf=(ROOT/".github/workflows/b62g5-real-servlet-revocation-seams.yml").read_text()
        for phrase in ("github.event.pull_request.head.sha",
                       "persist-credentials: false", "mvnw -B -ntp -f pom.xml test",
                       "B62G5DirectRevocationServletTest"):
            self.assertIn(phrase,wf)
        self.assertNotIn("deploy",wf.lower())

if __name__=="__main__":
    unittest.main()
