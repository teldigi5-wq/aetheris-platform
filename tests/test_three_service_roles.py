"""Review-only guard for synthetic shared-DB least-privilege evidence.

These checks do not replace actual PostgreSQL, Flyway, or Java execution.
"""
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/security/rehearse_shared_db_three_roles.sh"
APP = ROOT / "audit-service/src/main/resources/application.yml"
AUDIT_TEST = ROOT / "audit-service/src/test/java/io/aetheris/audit/SharedDatabaseAuditSchemaIsolationTest.java"
WORKFLOW = ROOT / ".github/workflows/security-three-service-role-isolation.yml"


class ThreeServiceRoleBoundaryContract(unittest.TestCase):
    def test_shell_script_is_parseable(self):
        subprocess.run(["bash", "-n", str(SCRIPT)], check=True)

    def test_audit_default_is_fail_closed(self):
        app = APP.read_text()
        self.assertIn("enabled: ${AETHERIS_AUDIT_FLYWAY_MIGRATION_ENABLED:false}", app)
        self.assertIn("baseline-on-migrate: false", app)
        self.assertIn("clean-disabled: true", app)
        self.assertIn("validate-on-migrate: true", app)
        self.assertIn("table: flyway_audit_schema_history", app)

    def test_audit_test_uses_real_jdbc_flyway_without_baselining(self):
        code = AUDIT_TEST.read_text()
        for phrase in (
            "aetheris.audit.shared.synthetic",
            "@JdbcTest(",
            "AutoConfigureTestDatabase.Replace.NONE",
            "spring.flyway.enabled=true",
            "spring.flyway.baseline-on-migrate=false",
            "spring.flyway.clean-disabled=true",
            "audit_stage.flyway_audit_schema_history",
            "audit_stage' AND table_name='audit_events'",
        ):
            self.assertIn(phrase, code)

    def test_real_disposable_role_login_and_refusal_contract(self):
        script = SCRIPT.read_text()
        for phrase in (
            "AETHERIS_THREE_ROLE_SYNTHETIC_ONLY",
            "trap cleanup EXIT",
            "postgres:16-alpine",
            "-p 127.0.0.1:55436:5432",
            "CREATE ROLE identity_migrator",
            "CREATE ROLE user_migrator",
            "CREATE ROLE audit_migrator",
            "CREATE ROLE identity_runtime",
            "CREATE ROLE user_runtime",
            "CREATE ROLE audit_runtime",
            "CREATE SCHEMA identity_stage AUTHORIZATION identity_migrator",
            "CREATE SCHEMA user_stage AUTHORIZATION user_migrator",
            "CREATE SCHEMA audit_stage AUTHORIZATION audit_migrator",
            "REVOKE ALL ON DATABASE shared_synthetic FROM PUBLIC",
            "REVOKE ALL ON SCHEMA public FROM PUBLIC",
            "PGPASSWORD",
            "-h 127.0.0.1",
            "run_all_migrations",
            "assert_denied",
            "DELETE FROM identity_stage.flyway_schema_history",
            "audit_stage.flyway_audit_schema_history",
            "THREE_ROLE_SECOND_START_HISTORY_AND_SYNTHETIC_ROWS_PRESERVED",
            "pg_dump",
            "pg_restore",
            "THREE_ROLE_UNKNOWN_AUDIT_LEGACY_REFUSED_WITH_SENTINEL_PRESERVED",
            "THREE_ROLE_AUDIT_REAL_CONCURRENT_FIRST_START_ONE_MIGRATION_SUCCESS",
            "ConcurrentAuditFlywayMigrationTest",
            "THREE_ROLE_UNKNOWN_AUDIT_LEGACY_FAILED_FOR_UNRELATED_REASON",
            "STAGE45_NO_GO",
        ):
            self.assertIn(phrase, script)
        for forbidden in (
            "docker compose down -v",
            "flyway clean",
            "DROP DATABASE",
            "DROP SCHEMA",
        ):
            self.assertNotIn(forbidden, script)

    def test_real_concurrent_flyway_migrator_contract(self):
        source = (ROOT / "audit-service/src/test/java/io/aetheris/audit/ConcurrentAuditFlywayMigrationTest.java").read_text()
        for requirement in (
            'aetheris.audit.concurrent.synthetic',
            'jdbc:postgresql://127.0.0.1:55436/shared_synthetic?currentSchema=audit_race',
            'CountDownLatch',
            'Executors.newFixedThreadPool(2)',
            'start.countDown()',
            'migrationsExecuted',
            'assertEquals(1, executions',
            'flyway_audit_schema_history',
            'baselineOnMigrate(false)',
            'cleanDisabled(true)',
            'assertTimeoutPreemptively',
        ):
            self.assertIn(requirement, source)

    def test_identity_to_user_owner_binding_is_explicit_and_not_api_enabled(self):
        source = (ROOT / "user-service/src/test/java/io/aetheris/users/security/SyntheticUserOwnershipBindingTest.java").read_text()
        users_filter = (ROOT / "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java").read_text()
        for required in (
            'aetheris.object.binding.synthetic',
            '@JdbcTest(',
            'spring.flyway.enabled=false',
            'synthetic_user_owner_bindings',
            'identity_uid=? AND user_id=? AND tenant_id=?::uuid',
            'assertFalse(hasExplicitBinding(userA, userA, TENANT_A))',
            'assertFalse(hasExplicitBinding(userB, userB, TENANT_B))',
            'assertFalse(hasExplicitBinding(userB, userA, TENANT_B))',
        ):
            self.assertIn(required, source)
        self.assertIn("return privileged && scopes.contains(\"users:read\");", users_filter)
        self.assertNotIn('SyntheticUserOwnershipBindingTest', users_filter)
        script = SCRIPT.read_text()
        for required in (
            'CREATE TABLE user_stage.synthetic_user_owner_bindings',
            'identity_uid BIGINT NOT NULL UNIQUE',
            'tenant_id UUID NOT NULL',
            'GRANT SELECT ON user_stage.synthetic_user_owner_bindings TO user_runtime',
            'assert_denied user_runtime "$usrp" "DELETE FROM user_stage.synthetic_user_owner_bindings;"',
            'assert_denied identity_migrator "$idmp" "SELECT * FROM user_stage.synthetic_user_owner_bindings;"',
            'THREE_ROLE_SYNTHETIC_CROSS_TENANT_OBJECT_OWNERSHIP_DENIALS_PASS',
        ):
            self.assertIn(required, script)

    def test_signed_tenant_claim_is_synthetic_and_cannot_enable_runtime_consumer(self):
        path = ROOT / "user-service/src/test/java/io/aetheris/users/security/SyntheticSignedTenantBindingTest.java"
        source = path.read_text()
        for assertion in (
            "aetheris.signed.binding.synthetic",
            'spring.flyway.enabled=false',
            'Jwts.parser().verifyWith(TEST_KEY)',
            'identity_uid=? AND user_id=? AND tenant_id=?::uuid',
            'claims.get("tenant_id", String.class)',
            'assertTrue(admitsSyntheticObject(consumer(b, TENANT_A), a))',
            'assertFalse(admitsSyntheticObject(consumer(a, TENANT_A), a))',
            'assertFalse(admitsSyntheticObject(consumer(b, TENANT_B), a))',
            'token(WRONG_SECRET',
            'verifyNoInteractions(service)',
            '.andExpect(status().isForbidden())',
            'X-Aetheris-Tenant-Id',
        ):
            self.assertIn(assertion, source)
        script = SCRIPT.read_text()
        self.assertIn('SyntheticSignedTenantBindingTest aetheris.signed.binding.synthetic', script)
        self.assertIn('THREE_ROLE_SIGNED_TENANT_BINDING_AND_HTTP_DENY_PROOF_PASS', script)
        user_filter = (ROOT / "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java").read_text()
        self.assertIn('return privileged && scopes.contains("users:read");', user_filter)
        issuer = (ROOT / "identity-service/src/main/java/io/aetheris/identity/JwtService.java").read_text()
        self.assertNotIn('.claim("tenant_id"', issuer)

    def test_both_backend_filters_have_opt_in_strict_issuer_audience_policy(self):
        for service, namespace in (("user-service", "users"), ("audit-service", "audit")):
            root = ROOT / service / "src"
            source = (root / "main/java/io/aetheris" / namespace / "security/DirectApiJwtFilter.java").read_text()
            tests = (root / "test/java/io/aetheris" / namespace / "security/DirectApiIssuerAudiencePolicyTest.java").read_text()
            for phrase in (
                'AETHERIS_BACKEND_JWT_ISSUER',
                'AETHERIS_BACKEND_JWT_AUDIENCE',
                'issuerAudiencePolicyReady',
                'strictIssuerAudience',
                'claims.getIssuer()',
                'claims.getAudience()',
                'claims.getAudience().size() != 1',
                'HttpServletResponse.SC_SERVICE_UNAVAILABLE',
                'Signed token issuer or audience mismatch',
            ):
                self.assertIn(phrase, source)
            for test in (
                'validExactIssuerAndSoleAudiencePermitOnlyAlreadyPrivilegedRoute',
                'missingWrongAndMultiAudienceTokensAreDeniedBeforeController',
                'incompleteOrMalformedOperatorPolicyFailsClosedWith503',
                'defaultLegacyModeDoesNotSilentlyGrantNewPermissions',
                'verifyNoInteractions(service)',
            ):
                self.assertIn(test, tests)
            self.assertNotIn('tenant_id', source)

    def test_identity_issuer_and_gateway_per_route_audience_are_opt_in(self):
        identity = (ROOT / "identity-service/src/main/java/io/aetheris/identity/JwtService.java").read_text()
        identity_test = (ROOT / "identity-service/src/test/java/io/aetheris/identity/JwtIssuerAudienceIssuanceTest.java").read_text()
        gateway = (ROOT / "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java").read_text()
        gateway_test = (ROOT / "gateway/src/test/java/io/aetheris/gateway/GatewayTargetedJwtContractTest.java").read_text()
        for text_fragment in (
            "AETHERIS_IDENTITY_JWT_ISSUER", "AETHERIS_IDENTITY_JWT_AUDIENCE",
            'builder.issuer(issuer).claim("aud", java.util.List.of(audience))',
            "Identity issuer/audience policy incomplete or invalid",
        ):
            self.assertIn(text_fragment, identity)
        self.assertNotIn('.claim("tenant_id"', identity)
        for text_fragment in (
            "AETHERIS_GATEWAY_JWT_ISSUER",
            "AETHERIS_GATEWAY_JWT_USERS_AUDIENCE",
            "AETHERIS_GATEWAY_JWT_AUDIT_AUDIENCE",
            "AETHERIS_GATEWAY_JWT_ORCHESTRATOR_AUDIENCE",
            "strictTargetedMode",
            "claims.getAudience().size() != 1",
            "headers.remove(\"X-Aetheris-Tenant-Id\")",
            "HttpStatus.SERVICE_UNAVAILABLE",
        ):
            self.assertIn(text_fragment, gateway)
        for method in (
            "targetedIssuerProducesRealSingleAudienceSignedJwt",
            "legacyDefaultsPreserveUnchangedClaimContract",
            "partialBlankOrMalformedTargetSettingsFailClosedOnConstruction",
        ):
            self.assertIn(method, identity_test)
        for method in (
            "exactSignedTargetAdmitsOnlyTheIntendedServiceRoute",
            "missingIssuerMissingAudienceAndMultipleRecipientsAreRejected",
            "partiallyConfiguredRouteFailsClosedWith503NotLegacyFallback",
            "emptyPolicyPreservesLegacyWhileStrippingSpoofedTenantHeader",
        ):
            self.assertIn(method, gateway_test)

    def test_disabled_by_default_typed_token_mint_stays_internal(self):
        issuer = (ROOT / "identity-service/src/main/java/io/aetheris/identity/JwtService.java").read_text()
        target = (ROOT / "identity-service/src/main/java/io/aetheris/identity/ApprovedTokenAudience.java").read_text()
        tests = (ROOT / "identity-service/src/test/java/io/aetheris/identity/ApprovedAudienceTokenIssuanceTest.java").read_text()
        controller = (ROOT / "identity-service/src/main/java/io/aetheris/identity/AuthController.java").read_text()
        for phrase in (
            "AETHERIS_IDENTITY_TARGETED_TOKENS_ENABLED:false",
            "issueForApprovedAudience(IdentityAccount account, ApprovedTokenAudience target)",
            "approvedTargetIssuanceEnabled",
            "Math.min(expirationSeconds, 300L)",
            "Role cannot request target-scoped token",
            'claim("aud", java.util.List.of(target.audience()))',
        ):
            self.assertIn(phrase, issuer)
        for phrase in (
            'USER("aetheris-user-api"',
            'AUDIT("aetheris-audit-api"',
            'ORCHESTRATOR("aetheris-orchestrator-api"',
            "role != Role.ADMIN && role != Role.DEVELOPER",
            "result.retainAll(scopes)",
        ):
            self.assertIn(phrase, target)
        for phrase in (
            "fixedApprovedTargetsYieldDistinctSignedRecipientsAndStrictScopedClaims",
            "developerGetsOnlyItsOriginalIntersectedScopes",
            "consumerAndMissingIdentityAreRefusedBeforeMinting",
            "mintingIsDisabledByDefaultAndRejectsUnpairedOperatorSettings",
            "shorterConfiguredTtlIsNotExtended",
        ):
            self.assertIn(phrase, tests)
        self.assertNotIn("issueForApprovedAudience", controller)
        self.assertNotIn(".claim(\"tenant_id\"", issuer)

    def test_gateway_signed_principal_claims_mirror_backend_denials(self):
        gateway = (ROOT / "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java").read_text()
        boundary = (ROOT / "gateway/src/test/java/io/aetheris/gateway/GatewayPrincipalBoundaryTest.java").read_text()
        jwt_baseline = (ROOT / "identity-service/src/main/java/io/aetheris/identity/Role.java").read_text()
        for role in ("ADMIN", "DEVELOPER", "API_CONSUMER"):
            self.assertIn(role, jwt_baseline)
            self.assertIn(role, gateway)
        for expected in (
            "verifiedScopes(claims.get(\"scopes\"))",
            "allowedByRole.containsAll(scopes)",
            "userId instanceof Long || userId instanceof Integer",
            "issuedAt.isAfter(Instant.now().plusSeconds(30))",
            "expires.isAfter(issuedAt)",
            'role.equals("API_CONSUMER")',
            "Direct backend access requires privileged role",
            "ROLE_SCOPES = Map.of(",
            "Collection<?> values",
        ):
            self.assertIn(expected, gateway)
        for case in (
            "validConsumerOrchestratorReadAndPrivilegedUserAuditRequestsWork",
            "consumerCannotReadDirectUsersOrAuditRegardlessOfLegacyScopes",
            "forgedRoleOrClaimedScopeOutsideActualRoleCeilingIsUnauthorized",
            "untrustedUidFormatAndMissingOrBadTimestampsNeverBecomePrincipal",
            "untypedScopesNullScopesMixedCollectionAndOversizedScopeAreDenied",
        ):
            self.assertIn(case, boundary)
        self.assertNotIn("readScopes(claims.get", gateway)

    def test_ci_exact_head_nonproduction_and_gate(self):
        workflow = WORKFLOW.read_text()
        for phrase in (
            "persist-credentials: false",
            "github.event.pull_request.head.sha",
            "AETHERIS_THREE_ROLE_SYNTHETIC_ONLY=yes",
            "rehearse_shared_db_three_roles.sh",
            "test_three_service_roles.py",
            "security_closure_preflight.py --assert-blocked",
            "if: always()",
        ):
            self.assertIn(phrase, workflow)


if __name__ == "__main__":
    unittest.main()
