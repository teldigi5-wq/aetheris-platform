"""B57 source invariants are defense-in-depth; real Java tests prove behavior."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CONFIG=("identity-service","gateway","user-service","audit-service")
FILES={
    "identity-service":"identity-service/src/main/java/io/aetheris/identity/JwtService.java",
    "gateway":"gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java",
    "user-service":"user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
    "audit-service":"audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java",
}


class CoordinatedIssuerAudienceTests(unittest.TestCase):
    def test_all_four_application_yml_configs_have_same_named_claims(self):
        for service in CONFIG:
            with self.subTest(service=service):
                s=(ROOT/service/"src/main/resources/application.yml").read_text()
                self.assertIn("issuer: ${AETHERIS_JWT_ISSUER:aetheris-identity}",s)
                self.assertIn("audience: ${AETHERIS_JWT_AUDIENCE:aetheris-api}",s)

    def test_identity_real_issuer_signs_both_claims(self):
        text=(ROOT/FILES["identity-service"]).read_text()
        self.assertIn(".issuer(issuer)",text)
        self.assertIn(".audience().add(audience).and()",text)
        self.assertIn("signWith(key)",text)
        self.assertIn("@Autowired",text)

    def test_every_consumer_requires_both_claims_in_signature_parser(self):
        for service in ("gateway","user-service","audit-service"):
            with self.subTest(service=service):
                text=(ROOT/FILES[service]).read_text()
                self.assertIn(".verifyWith(key)",text)
                self.assertIn(".requireIssuer(issuer)",text)
                self.assertIn(".requireAudience(audience)",text)
                self.assertIn(".parseSignedClaims(token)",text)

    def test_malformed_issuer_or_audience_refused_at_configuration(self):
        for service in CONFIG:
            s=(ROOT/FILES[service]).read_text()
            self.assertIn("issuer == null || issuer.isBlank()",s)
            self.assertIn("audience == null || audience.isBlank()",s)
            self.assertIn("aetheris-identity",s)
            self.assertIn("aetheris-api",s)

    def test_original_gateway_admin_and_scope_controls_not_removed(self):
        s=(ROOT/FILES["gateway"]).read_text()
        self.assertIn('if (!"ADMIN".equals(role))',s)
        self.assertIn("Missing required scope",s)
        self.assertIn("authorizationHeaders.size() != 1",s)
        self.assertIn('headers.remove("X-Aetheris-User-Role")',s)

    def test_backend_administrator_only_controls_not_removed(self):
        for service in ("user-service","audit-service"):
            s=(ROOT/FILES[service]).read_text()
            self.assertIn('Objects.equals(role, "ADMIN")',s)
            self.assertIn('response.sendError(HttpServletResponse.SC_FORBIDDEN',s)
            self.assertIn('response.sendError(HttpServletResponse.SC_UNAUTHORIZED',s)
            self.assertIn("isPositiveIntegralUid(uid)",s)

    def test_b57_new_behavioral_negative_tests_in_all_consumers(self):
        for service,path in [
            ("gateway","gateway/src/test/java/io/aetheris/gateway/JwtAuthenticationFilterTest.java"),
            ("user-service","user-service/src/test/java/io/aetheris/users/security/DirectApiJwtFilterTest.java"),
            ("audit-service","audit-service/src/test/java/io/aetheris/audit/security/DirectApiJwtFilterTest.java")]:
            with self.subTest(service=service):
                s=(ROOT/path).read_text()
                self.assertIn("wrong",s.lower())
                self.assertIn("null",s)
                self.assertIn("isUnauthorized()",s if service!="gateway" else "isUnauthorized()"+s)
        identity=(ROOT/"identity-service/src/test/java/io/aetheris/identity/JwtIssuerAudienceB57Test.java").read_text()
        self.assertIn("mismatchedIssuerAndAudienceMustNotValidate",identity)

    def test_existing_real_identity_issued_jwt_http_contract_still_present(self):
        for service,path in [
            ("user-service","user-service/src/test/java/io/aetheris/users/security/IdentityIssuedJwtContractIT.java"),
            ("audit-service","audit-service/src/test/java/io/aetheris/audit/security/IdentityIssuedJwtContractIT.java"),
            ("gateway","gateway/src/test/java/io/aetheris/gateway/IdentityIssuedJwtContractIT.java")]:
            with self.subTest(service=service):
                self.assertIn("IdentityIssuedJwtContractIT",path)
                self.assertTrue((ROOT/path).is_file())


if __name__=="__main__":
    unittest.main()
