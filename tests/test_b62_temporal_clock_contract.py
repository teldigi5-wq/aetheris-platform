"""B62-A regression: claim dates and previous-key deadline share one trusted clock.

Static checks complement signed-JWT Java integration tests; not a replacement
for real JJWT, end-to-end session rollout or production clock certification.
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

class B62TemporalClockContract(unittest.TestCase):
    def test_no_mixed_system_clock_in_claim_checks(self):
        for path in FILTERS:
            with self.subTest(path=path):
                src = (ROOT / path).read_text()
                self.assertNotIn("Instant.now()", src)
                self.assertEqual(src.count("Instant requestNow = clock.instant();"), 1)
                self.assertIn("requestNow.plusSeconds(30)", src)
                self.assertIn("isAfter(requestNow)", src)
                self.assertIn("!clock.instant().isBefore(previousKeyNotAfter)", src)

    def test_all_real_signed_jwt_suites_advance_same_verifier_clock(self):
        for path in TESTS:
            with self.subTest(path=path):
                src = (ROOT / path).read_text()
                self.assertIn("tokenIssueAndExpiryChecksUseSameInjectedClockAsKeyRetirement", src)
                self.assertIn("wallNow.minusSeconds(180)", src)
                self.assertIn('check(same, "current", false)', src)
                self.assertIn('check(same, "previous", true)', src)
                self.assertIn("clock.advanceTo(wallNow.plusSeconds(1500))", src)

if __name__ == "__main__":
    unittest.main()
