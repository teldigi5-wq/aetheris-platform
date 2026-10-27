"""Direct backend JWT authorization safety policy for legacy source branch.

Full authentication evidence requires the actual servlet/JJWT tests below.
This guard never certifies tenant isolation or RS256 key separation.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
MODULES=[
  "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
  "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java",
]

class DirectBackendAuthSourceGuard(unittest.TestCase):
    def test_both_services_enforce_signed_backend_requests(self):
        for name in MODULES:
            s=(ROOT/name).read_text()
            for marker in ("@Component","@Autowired","OncePerRequestFilter",
                '@Value("${AETHERIS_JWT_SECRET:}")',
                "parseSignedClaims(token)", "verifyWith(key)",
                "claims.getIssuedAt()", "claims.getExpiration()",
                'role.equals("ADMIN") || role.equals("DEVELOPER")',
                "chain.doFilter(request, response);",
                "key == null", "HttpServletResponse.SC_SERVICE_UNAVAILABLE",
                "HttpServletResponse.SC_UNAUTHORIZED",
                "HttpServletResponse.SC_FORBIDDEN"):
                self.assertIn(marker,s)
            self.assertNotIn("X-Aetheris-User-Id\")",s) # ignore forwarded identity headers
            self.assertNotIn("setHeader(\"Authorization\"",s)
            self.assertNotIn('secret.length() < 32) this.key',s)

    def test_implicit_cross_database_user_id_equality_never_grants_ownership(self):
        s=(ROOT/MODULES[0]).read_text()
        self.assertIn("Equal numbers DO NOT establish ownership",s)
        self.assertNotIn("resourceId == signedUid",s)
        self.assertIn('return privileged && scopes.contains("users:read");',s)
        self.assertIn('scopes.contains("users:write")',s)
        self.assertIn('id.matches("[1-9][0-9]{0,18}")',s)

    def test_legacy_signer_source_contract_not_claimed_rs256(self):
        for mod in ("user-service","audit-service"):
            xml=(ROOT/mod/"pom.xml").read_text()
            for fragment in ("jjwt-api","jjwt-jackson","jjwt-impl",
                             "<jjwt.version>0.12.6</jjwt.version>"):
                self.assertIn(fragment,xml)
            t=(ROOT/mod/"src/test/java/io/aetheris"/
               ("users" if mod=="user-service" else "audit")/
               "security/DirectApiJwtFilterTest.java").read_text()
            for fragment in ("MockMvcBuilders.standaloneSetup",
                             ".addFilters(new DirectApiJwtFilter",
                             "verifyNoInteractions(service)",
                             "isUnauthorized()", "isForbidden()",
                             "isServiceUnavailable()", "isOk()"):
                self.assertIn(fragment,t)

if __name__=="__main__":
    unittest.main()
