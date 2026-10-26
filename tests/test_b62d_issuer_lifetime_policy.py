"""B62-D: production JWT issuer expiry bounded without weakening signed negative fixtures."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ISSUER = ROOT / "identity-service/src/main/java/io/aetheris/identity/JwtService.java"
RSA_B59 = ROOT / "identity-service/src/test/java/io/aetheris/identity/IdentityRs256B59ContractIT.java"
RSA_B60 = ROOT / "identity-service/src/test/java/io/aetheris/identity/IdentityRs256B60RotationIT.java"
HMAC = ROOT / "identity-service/src/test/java/io/aetheris/identity/IdentityIssuedJwtContractIT.java"

class B62DIssuerBoundary(unittest.TestCase):
    def test_no_production_bypass_or_configurable_cap(self):
        src = ISSUER.read_text()
        self.assertIn("MAX_ACCESS_TOKEN_LIFETIME_SECONDS = 3600L", src)
        self.assertIn("expirationSeconds < 1 || expirationSeconds > MAX_ACCESS_TOKEN_LIFETIME_SECONDS", src)
        self.assertIn("now.plusSeconds(expirationSeconds)", src)
        self.assertNotIn("allow-invalid-expiration", src)
        self.assertNotIn("bypassLifetime", src)

    def test_invalid_fixtures_stay_in_test_source_only(self):
        b59 = RSA_B59.read_text()
        b60 = RSA_B60.read_text()
        hmac = HMAC.read_text()
        for src in (b59, b60):
            self.assertIn("Jwts.builder()", src)
            self.assertIn("signWith(key.getPrivate(), Jwts.SIG.RS256)", src)
            self.assertIn("JwtService.MAX_ACCESS_TOKEN_LIFETIME_SECONDS", src)
        self.assertIn("expiredAt.minusSeconds(120)", hmac)
        self.assertNotIn("new JwtService(TEST_SECRET, -120)", hmac)
        self.assertIn("TEST_SECRET.getBytes", hmac)

    def test_boundary_unit_cases_present(self):
        src = (ROOT / "identity-service/src/test/java/io/aetheris/identity/JwtServiceLifetimeB62DTest.java").read_text()
        for value in ("-120L", "-1L", "0L", "3601L", "Long.MAX_VALUE", "3600L", "1L"):
            self.assertIn(value, src)
        self.assertIn("Duration.between(", src)

if __name__ == "__main__":
    unittest.main()
