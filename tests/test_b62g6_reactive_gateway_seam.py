"""B62-G6: test-only reactive Gateway filter seam and fail-closed policy guards."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
FILTER=(ROOT/"gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java").read_text()
TEST=(ROOT/"gateway/src/test/java/io/aetheris/gateway/B62G6ReactiveGatewayRevocationTest.java").read_text()
DOC=(ROOT/"docs/security/B62G6_REACTIVE_GATEWAY_SEAM_20261027.md").read_text()
WORKFLOW=(ROOT/".github/workflows/b62g6-reactive-gateway-revocation.yml").read_text()

class B62G6Contract(unittest.TestCase):
    def test_reactive_injection_is_package_private_and_not_spring_wired(self):
        for clause in (
            "interface TestOnlyReactiveRevocation",
            "Mono<Boolean> allowed(long verifiedUid",
            "private final TestOnlyReactiveRevocation b62g6TestOnlyRevocation;",
            "previousKid, previousNotAfter, clock, null);",
            "this.b62g6TestOnlyRevocation = revocation;",
            'revocation != null && !"rs256".equals(mode)',
            "if (b62g6TestOnlyRevocation != null) {",
            "Mono.defer(() -> {",
            ".timeout(B62G6_TEST_ONLY_LOOKUP_TIMEOUT)",
            ".onErrorReturn(false)",
            ".defaultIfEmpty(false)",
            "forwardVerified(exchange, chain",
        ):
            self.assertIn(clause,FILTER)
        self.assertIn("Duration.ofMillis(200)",FILTER)
        self.assertNotIn("@Bean",FILTER)
        self.assertNotIn("block()",FILTER)
        self.assertNotIn("jdbc:",FILTER)
        self.assertNotIn("revocation-enabled",FILTER)

    def test_identity_forwarding_happens_only_after_revocation_allow(self):
        self.assertLess(FILTER.index("requireAudience(audience)"),
                        FILTER.index("if (b62g6TestOnlyRevocation != null) {"))
        self.assertLess(FILTER.index('if (requiredScope != null && !scopes.contains(requiredScope))'),
                        FILTER.index("if (b62g6TestOnlyRevocation != null) {"))
        self.assertLess(FILTER.index(".defaultIfEmpty(false)"),
                        FILTER.index("exchange.getAttributes().put(VERIFIED_USER_ID_ATTRIBUTE"))
        self.assertLess(FILTER.index("if (b62g6TestOnlyRevocation != null) {"),
                        FILTER.index("return forwardVerified(exchange, chain, userId, email, role, scopes);"))
        self.assertIn("Mono.error(new IllegalStateException",FILTER)

    def test_actual_gateway_filter_signing_and_nonblocking_timeout_regressions(self):
        for clause in (
            "generator.initialize(3072)",
            "Jwts.SIG.RS256",
            "MockServerWebExchange",
            "JwtAuthenticationFilter f = filter(",
            "Mono.delay(Duration.ofMillis(15))",
            "Mono.never()",
            "Mono.empty()",
            "Mono.error(",
            "assertEquals(0, calls.get()",
            "assertNull(e.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE))",
            "HttpStatus.UNAUTHORIZED",
            "HttpStatus.FORBIDDEN",
            "void injectedProofCannotBeConfiguredForLegacyHmacVerifier()"
        ):
            self.assertIn(clause,TEST)
        self.assertNotIn("mock(JwtAuthenticationFilter.class)",TEST)

    def test_release_constraints_are_explicit(self):
        for phrase in ("NOT INTEGRATED", "NO_GO", "production", "Gateway",
                       "User", "Audit", "owner approval", "database migration",
                       "read-after-write", "nonblocking", "certificate"):
            self.assertIn(phrase,DOC)
        for phrase in ("github.event.pull_request.head.sha",
                       "persist-credentials: false",
                       "mvnw -B -ntp -f pom.xml test",
                       "B62G6ReactiveGatewayRevocationTest"):
            self.assertIn(phrase,WORKFLOW)

if __name__=="__main__":
    unittest.main()
