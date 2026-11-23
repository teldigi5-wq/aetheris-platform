"""B62-B: bounded signed access-token lifetime in every API verifier."""
from pathlib import Path
import unittest

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

class B62BPolicy(unittest.TestCase):
    def test_uniform_nonrelaxable_maximum(self):
        for path in FILTERS:
            with self.subTest(path=path):
                src = (ROOT / path).read_text()
                self.assertEqual(src.count("MAX_ACCESS_TOKEN_LIFETIME_SECONDS = 3600L"), 1)
                self.assertIn("java.time.Duration.between(", src)
                self.assertIn("java.time.Duration.ofSeconds(MAX_ACCESS_TOKEN_LIFETIME_SECONDS)", src)
                self.assertIn("!expires.toInstant().isAfter(issued.toInstant())" if "DirectApi" in path
                              else "!expiration.toInstant().isAfter(issuedAt.toInstant())", src)
                self.assertIn("isAfter(requestNow)", src)
                self.assertIn("requestNow.plusSeconds(30)", src)
                self.assertIn("!clock.instant().isBefore(previousKeyNotAfter)", src)

    def test_gateway_enforces_cap_before_route_specific_authorization(self):
        src = (ROOT / FILTERS[0]).read_text()
        self.assertLess(src.index("Duration.ofSeconds(MAX_ACCESS_TOKEN_LIFETIME_SECONDS)"),
                        src.index("if (isAdministrativeApi(path))"))

    def test_identity_signed_max_boundaries_in_each_real_verifier(self):
        source = (ROOT / "identity-service/src/test/java/io/aetheris/identity/IdentityRs256B60RotationIT.java").read_text()
        for needle in ('"max-lifetime"', '"overlong-lifetime"', '"zero-lifetime"'):
            self.assertIn(needle, source)
        for path in TESTS:
            with self.subTest(path=path):
                src = (ROOT / path).read_text()
                self.assertIn("actualIdentitySignedAccessTokensAreBoundedToOneHour", src)
                self.assertIn('check(same, "max-lifetime", true)', src)
                self.assertIn('check(same, "overlong-lifetime", false)', src)
                self.assertIn('check(same, "zero-lifetime", false)', src)

if __name__ == "__main__":
    unittest.main()
