"""B62-C: all pinned JWT parsers honor the same verifier UTC clock.

Static contract augments genuine Identity-signed JJWT/not-before integration;
it is not a substitute for Java or end-to-end host time attestation.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILTERS = (
    "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java",
    "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
    "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java",
)
TESTS = (
    "gateway/src/test/java/io/aetheris/gateway/IdentityRs256B61DeadlineIT.java",
    "user-service/src/test/java/io/aetheris/users/security/IdentityRs256B61DeadlineIT.java",
    "audit-service/src/test/java/io/aetheris/audit/security/IdentityRs256B61DeadlineIT.java",
)

class B62CParserClockPolicy(unittest.TestCase):
    def test_all_three_jjwt_verifier_branches_share_injected_utc_clock(self):
        for path in FILTERS:
            with self.subTest(path=path):
                src=(ROOT/path).read_text()
                self.assertEqual(src.count("Jwts.parser().clock(() -> Date.from(clock.instant()))"), 3)
                self.assertEqual(src.count(".clockSkewSeconds(0).verifyWith("), 3)
                self.assertIn("!clock.instant().isBefore(previousKeyNotAfter)", src)
                self.assertIn("MAX_ACCESS_TOKEN_LIFETIME_SECONDS = 3600L", src)
                self.assertNotIn("Jwts.parser().verifyWith(", src)

    def test_real_identity_fixture_proves_nbf_and_exp_with_injected_clock(self):
        issuer=(ROOT/"identity-service/src/test/java/io/aetheris/identity/IdentityRs256B60RotationIT.java").read_text()
        self.assertIn(".notBefore(Date.from(notBefore))", issuer)
        self.assertIn('tokens.put("future-nbf", futureNotBefore)', issuer)
        self.assertIn('out.resolve("future-nbf.epoch")', issuer)
        for path in TESTS:
            with self.subTest(path=path):
                src=(ROOT/path).read_text()
                self.assertIn("jwtParserUsesVerifierClockForExactNotBeforeAndExpiredBoundaries", src)
                self.assertIn('check(same, "future-nbf", false)', src)
                self.assertIn('check(same, "future-nbf", true)', src)
                self.assertIn("clock.advanceTo(nbf)", src)
                self.assertIn("clock.advanceTo(nbf.plusSeconds(901))", src)

if __name__ == "__main__":
    unittest.main()
