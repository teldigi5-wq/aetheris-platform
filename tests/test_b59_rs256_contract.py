"""B59 source-level constraints; Java+real Docker proof is the acceptance test."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SERVICES=("identity-service","gateway","user-service","audit-service")

class B59AsymmetricBoundary(unittest.TestCase):
    def test_identity_private_key_never_configured_on_resource_services(self):
        identity=(ROOT/"identity-service/src/main/resources/application.yml").read_text()
        self.assertIn("rsa-private-key-pkcs8-base64",identity)
        self.assertNotIn("rsa-public-key-x509-base64",identity)
        for svc in SERVICES[1:]:
            with self.subTest(service=svc):
                y=(ROOT/svc/"src/main/resources/application.yml").read_text()
                self.assertIn("rsa-public-key-x509-base64",y)
                self.assertNotIn("rsa-private-key-pkcs8-base64",y)

    def test_every_service_has_opt_in_mode_no_implicit_activation(self):
        for svc in SERVICES:
            with self.subTest(service=svc):
                y=(ROOT/svc/"src/main/resources/application.yml").read_text()
                self.assertIn("mode: ${AETHERIS_JWT_MODE:hmac}",y)
                self.assertIn("secret: ${AETHERIS_JWT_SECRET:}",y)

    def test_real_identity_uses_only_private_rsa_signing(self):
        source=(ROOT/"identity-service/src/main/java/io/aetheris/identity/JwtService.java").read_text()
        for token in ("RSAPrivateCrtKey","getModulus().bitLength() < 3072",
                      "Jwts.SIG.RS256","signWith(rsaPrivateKey",
                      '"rs256".equals(mode)','"hmac".equals(mode)'):
            self.assertIn(token,source)
        self.assertNotIn("rsa-public-key-x509-base64",source)

    def test_three_consumers_have_only_public_rsa_verification(self):
        for p in (
            "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java",
            "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
            "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java",
        ):
            with self.subTest(file=p):
                s=(ROOT/p).read_text()
                for expected in ("X509EncodedKeySpec","RSAPublicKey","getModulus().bitLength() < 3072",
                    "Jwts.parser().clock(() -> Date.from(clock.instant()))",
                    ".clockSkewSeconds(0).verifyWith(rsaPublicKey)",'!"RS256".equals',
                    "rsaKid.equals(signed.getHeader().getKeyId())"):
                    self.assertIn(expected,s)
                self.assertNotIn("PKCS8EncodedKeySpec",s)
                self.assertNotIn("java.security.PrivateKey",s)

    def test_no_mode_silently_falls_back_to_a_shared_hmac_secret(self):
        for p in (
            "identity-service/src/main/java/io/aetheris/identity/JwtService.java",
            "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java",
            "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
            "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java",
        ):
            s=(ROOT/p).read_text()
            self.assertIn('"Unsupported JWT',s)
            self.assertIn('if ("hmac".equals(mode))',s)
            self.assertIn('else if ("rs256".equals(mode))',s)
            self.assertIn('kid.matches("[a-z][a-z0-9-]{0,30}")',s)

    def test_identity_rsa_private_key_never_exported_as_a_test_artifact(self):
        src=(ROOT/"identity-service/src/test/java/io/aetheris/identity/IdentityRs256B59ContractIT.java").read_text()
        self.assertIn('Files.writeString(dir.resolve("public-x509.b64")',src)
        self.assertNotIn('writeString(dir.resolve("private-',src)
        self.assertIn("B59_EPHEMERAL_IDENTITY_RSA_V1",src)

    def test_real_docker_runtime_has_public_key_only_and_no_hmac(self):
        config=(ROOT/"tests/security/docker-compose.b59-rsa-overlay.yml").read_text()
        for name in ("gateway","user-service","audit-service"):
            self.assertIn(name+":",config)
        self.assertIn('AETHERIS_JWT_SECRET: ""',config)
        self.assertIn("AETHERIS_JWT_MODE: rs256",config)
        self.assertNotIn("AETHERIS_JWT_RSA_PRIVATE_KEY",config)
        self.assertIn("read_only: true",config)
        self.assertIn('cap_drop: ["ALL"]',config)

    def test_real_http_probe_has_four_endpoints_and_all_eight_token_cases(self):
        s=(ROOT/"tests/security/probe_b59_rsa.py").read_text()
        for t in ("direct-user","gateway-user","direct-audit","gateway-audit","legacy-hmac",
                  "wrong-signature","unknown-kid","wrong-issuer","wrong-audience",
                  "expired","tampered"):
            self.assertIn(t,s)
        self.assertIn("tests==32",s)

if __name__=="__main__":
    unittest.main()
