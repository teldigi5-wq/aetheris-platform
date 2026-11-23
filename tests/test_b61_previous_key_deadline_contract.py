"""B61 static guardrails; Java and real HTTP are separately mandatory."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
FILTERS=(
"gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java",
"user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
"audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java",
)

class B61DeadlineContract(unittest.TestCase):
    def test_every_direct_and_gateway_filter_rechecks_old_key_time_per_request(self):
        for path in FILTERS:
            with self.subTest(path=path):
                s=(ROOT/path).read_text()
                self.assertIn("!clock.instant().isBefore(previousKeyNotAfter)",s)
                self.assertIn("if (previousKeyVerified &&",s)
                self.assertIn("Clock.systemUTC()",s)
                self.assertIn("this.clock = java.util.Objects.requireNonNull(clock",s)

    def test_no_previous_key_without_absolute_deadline(self):
        for path in FILTERS:
            s=(ROOT/path).read_text()
            self.assertIn('previousNotAfter.matches("[1-9][0-9]{9}")',s)
            self.assertIn("Cutoff without previous public key",s)
            self.assertIn("Previous public key requires strict UTC epoch cutoff",s)

    def test_expiration_is_bounded_at_startup_to_one_hour(self):
        for path in FILTERS:
            s=(ROOT/path).read_text()
            self.assertIn("MAX_PREVIOUS_KEY_GRACE_SECONDS = 3600L",s)
            self.assertIn("cutoff.isAfter(clock.instant().plusSeconds(MAX_PREVIOUS_KEY_GRACE_SECONDS))",s)

    def test_hmac_or_single_key_cannot_accept_orphan_deadline(self):
        for path in FILTERS:
            s=(ROOT/path).read_text()
            self.assertIn('(previousNotAfter != null && !previousNotAfter.isBlank())',s)
            self.assertIn('"hmac".equals(mode)',s)

    def test_real_signed_jwt_security_guards_still_apply(self):
        for path in FILTERS:
            s=(ROOT/path).read_text()
            self.assertIn("verifyWith(rsaPublicKey)",s)
            self.assertIn("verifyWith(rsaPreviousPublicKey)",s)
            self.assertIn("requireIssuer(issuer)",s)
            self.assertIn("requireAudience(audience)",s)
            self.assertIn("rsaKid.equals(signed.getHeader().getKeyId())",s)

    def test_all_api_yaml_configs_require_consistent_utc_setting(self):
        for svc in ("gateway","user-service","audit-service"):
            config=(ROOT/svc/"src/main/resources/application.yml").read_text()
            self.assertIn("previous-key-not-after-epoch-seconds: ${AETHERIS_JWT_PREVIOUS_KEY_NOT_AFTER_EPOCH_SECONDS:}",config)
        identity=(ROOT/"identity-service/src/main/resources/application.yml").read_text()
        self.assertNotIn("PREVIOUS_KEY_NOT_AFTER",identity)

    def test_each_java_test_proves_same_filter_instance_deadline_and_new_token_survival(self):
        for path in (
            "gateway/src/test/java/io/aetheris/gateway/IdentityRs256B61DeadlineIT.java",
            "user-service/src/test/java/io/aetheris/users/security/IdentityRs256B61DeadlineIT.java",
            "audit-service/src/test/java/io/aetheris/audit/security/IdentityRs256B61DeadlineIT.java",
        ):
            s=(ROOT/path).read_text()
            self.assertIn("AtomicReference<Instant>",s)
            self.assertIn("advanceTo(cutoff)",s)
            self.assertIn('"previous",false',s)
            self.assertIn('"current",true',s)
            self.assertIn("clock",s)

    def test_no_new_signing_key_store_or_production_deploy(self):
        for path in FILTERS:
            s=(ROOT/path).read_text()
            self.assertNotIn("https://jwks",s)
            self.assertNotIn("getJwkSet",s)
        assert (ROOT/"identity-service/src/main/java/io/aetheris/identity/JwtService.java").is_file()

if __name__=="__main__":
    unittest.main()
