"""B60 source guards; actual Java/JWT and Docker HTTP tests are required too."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
CONSUMERS={
 "gateway":"gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java",
 "user":"user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
 "audit":"audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java",
}

class B60BoundedPublicTrust(unittest.TestCase):
    def test_all_three_independent_filters_have_explicit_previous_public_key_and_kid(self):
        for name,p in CONSUMERS.items():
            with self.subTest(service=name):
                text=(ROOT/p).read_text()
                for guard in ("rsaPreviousPublicKey","rsaPreviousKid",
                    "rsa-previous-public-key-x509-base64",
                    "aetheris.jwt.previous-kid", "previousKeyVerified"):
                    self.assertIn(guard,text)

    def test_never_select_untrusted_header_to_load_a_key_or_url(self):
        for path in CONSUMERS.values():
            src=(ROOT/path).read_text()
            self.assertIn("verifyWith(rsaPublicKey)",src)
            self.assertIn("verifyWith(rsaPreviousPublicKey)",src)
            self.assertIn("rsaPreviousKid.equals(signed.getHeader().getKeyId())",src)
            self.assertIn("rsaKid.equals(signed.getHeader().getKeyId())",src)
            self.assertNotIn("https://",src)
            self.assertNotIn("getJwkSet",src)

    def test_hmac_mode_denies_configured_rsa_previous_public_key(self):
        for path in CONSUMERS.values():
            text=(ROOT/path).read_text()
            self.assertIn('(previousPublicKey != null && !previousPublicKey.isBlank())',text)
            self.assertIn('(previousKid != null && !previousKid.isBlank())',text)

    def test_partial_equal_or_weak_previous_keys_fail_startup(self):
        for path in CONSUMERS.values():
            text=(ROOT/path).read_text()
            self.assertIn('previousKid.equals(kid)',text)
            self.assertIn('previousPublicKey.length() > 16384',text)
            self.assertIn('previousRsa.getModulus().bitLength() < 3072',text)
            self.assertIn('java.util.Arrays.equals(candidate.getEncoded()',text)

    def test_three_app_properties_are_optional_and_default_disabled(self):
        for service in ("gateway","user-service","audit-service"):
            with self.subTest(service=service):
                y=(ROOT/service/"src/main/resources/application.yml").read_text()
                self.assertIn("previous-kid: ${AETHERIS_JWT_PREVIOUS_KID:}",y)
                self.assertIn("rsa-previous-public-key-x509-base64: ${AETHERIS_JWT_RSA_PREVIOUS_PUBLIC_KEY_X509_B64:}",y)
        self.assertNotIn("previous-kid:",(ROOT/"identity-service/src/main/resources/application.yml").read_text())

    def test_minted_two_keys_do_not_export_identity_private_key(self):
        text=(ROOT/"identity-service/src/test/java/io/aetheris/identity/IdentityRs256B60RotationIT.java").read_text()
        self.assertIn('current-public-x509.b64',text)
        self.assertIn('previous-public-x509.b64',text)
        self.assertIn('B60_EPHEMERAL_PUBLIC_ROLLOVER_V1',text)
        self.assertNotIn('Files.writeString(out.resolve("private',text)

    def test_disposable_compose_stays_public_only_and_retired_key_is_empty(self):
        overlap=(ROOT/"tests/security/docker-compose.b60-overlap-overlay.yml").read_text()
        retire=(ROOT/"tests/security/docker-compose.b60-retire-overlay.yml").read_text()
        self.assertIn('rsa-previous-public-key',overlap)
        self.assertIn('AETHERIS_JWT_RSA_PREVIOUS_PUBLIC_KEY_X509_B64',overlap)
        self.assertNotIn("AETHERIS_JWT_RSA_PRIVATE_KEY_PKCS8_B64",overlap)
        self.assertIn("read_only: true",overlap)
        self.assertIn('cap_drop: ["ALL"]',overlap)
        self.assertEqual(retire.count('AETHERIS_JWT_PREVIOUS_KID: ""'),3)
        self.assertEqual(retire.count('AETHERIS_JWT_RSA_PREVIOUS_PUBLIC_KEY_X509_B64: ""'),3)

    def test_real_http_probe_tracks_two_distinct_rotation_phases(self):
        probe=(ROOT/"tests/security/probe_b60_rsa_rotation.py").read_text()
        for needle in ('"overlap":{','"retired":{','"relabel-previous":401',
                       '"previous":200','"previous":401','tests==(28 if phase=="overlap" else 12)'):
            self.assertIn(needle,probe)
        self.assertNotIn("subprocess",probe)

if __name__=="__main__":
    unittest.main()
