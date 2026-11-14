package io.aetheris.identity;

import io.aetheris.orchestrator.security.RuntimePinnedPrincipalVerifier;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

/**
 * CI-ONLY CROSS-REPOSITORY CONTRACT PROBE, NOT a deployable service.
 *
 * Compiled with Identity's genuine JwtService.issue() from THIS Platform
 * draft and RuntimePinnedPrincipalVerifier.java from immutable Runtime
 * draft a772cb803539c982e6ecc7e9faa7b62a1a4a19cc. The separate
 * workflow validates the fetched Runtime blob and commit before compiling.
 *
 * Neither signed token identity nor scopes authorize an HTTP/SSE resource.
 * There is no B62 revocation lookup, private key persisted on disk,
 * customer credential, network listener, or production route admission.
 */
public final class IdentityRuntimeExactInteropProbe {
    private static final String ISSUER = "aetheris-identity";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "cross-repo-ci-a";
    private static final String TEST_HMAC_KEY =
        "synthetic-identity-hmac-not-a-customer-credential-32bytes";

    private IdentityRuntimeExactInteropProbe() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException rejected) {
            return;
        }
        throw new AssertionError("Fail-open verifier: " + message);
    }

    private static String publicPem(KeyPair key) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(key.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private static IdentityAccount syntheticAccount() throws Exception {
        IdentityAccount account = new IdentityAccount(
            "Synthetic", "identity-interoperability@example.invalid",
            "not-an-actual-password-hash", Role.DEVELOPER);
        Field id = IdentityAccount.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(account, 42L);
        require(account.getId() == 42L, "Synthetic ID not seeded");
        return account;
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 0, "No operator args, keys, paths or tokens accepted");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair identityKey = generator.generateKeyPair();
        KeyPair unrelatedKey = generator.generateKeyPair();

        IdentityAccount account = syntheticAccount();
        JwtService issuer = new JwtService(
            "", 300, ISSUER, AUDIENCE, "rs256",
            Base64.getEncoder().encodeToString(
                identityKey.getPrivate().getEncoded()),
            KID);

        RuntimePinnedPrincipalVerifier verifier = new RuntimePinnedPrincipalVerifier(
            publicPem(identityKey), KID, ISSUER, AUDIENCE, Clock.systemUTC());

        // REAL Identity JwtService.issue() tokens, not fixture Jwts.builder().
        String issued1 = issuer.issue(account);
        String issued2 = issuer.issue(account);
        require(!issued1.equals(issued2),
            "Distinct RS256 Identity issuance unexpectedly reuses a token");

        var first = verifier.verifyBearerForRevocation("Bearer " + issued1);
        var second = verifier.verifyBearerForRevocation("Bearer " + issued2);
        require(first.identityUid() == 42L, "Runtime rejected/misread signed UID");
        require(first.subject().equals(account.getEmail()), "Signed subject mismatch");
        require(first.role().equals("DEVELOPER"), "Role identity mismatch");
        require(first.scopes().equals(Role.DEVELOPER.scopes()),
            "Runtime and Platform role/scope contracts have diverged");
        require(first.hasScope("orchestrator:read"), "Required signed read scope missing");
        require(!first.hasScope("identity:write"), "Role escalation from signed claims");
        require(first.isFresh(), "New token already expired");

        var signed1 = first.requireRevocationFacts();
        var signed2 = second.requireRevocationFacts();
        require(signed1.signedIdentityUid() == 42L, "Revocation UID not signed");
        require(signed1.verifiedIssuer().equals(ISSUER), "Revocation issuer mismatch");
        require(signed1.verifiedKeyId().equals(KID), "Revocation key ID mismatch");
        require(!signed1.signedTokenId().equals(signed2.signedTokenId()),
            "Distinct real Identity-issued tokens have identical JTIs");
        UUID parsed = UUID.fromString(signed1.signedTokenId());
        require(parsed.version() == 4 && parsed.variant() == 2,
            "Signed Identity token ID must be UUIDv4");
        require(parsed.toString().equals(signed1.signedTokenId()),
            "Signed Identity token ID must be canonical");
        require(signed1.signedIssuedAt().isBefore(Instant.now().plusSeconds(30)),
            "Signed issued-at is unexpectedly in the future");

        RuntimePinnedPrincipalVerifier forgedSigner = new RuntimePinnedPrincipalVerifier(
            publicPem(unrelatedKey), KID, ISSUER, AUDIENCE, Clock.systemUTC());
        rejects(() -> forgedSigner.verifyBearerForRevocation("Bearer " + issued1),
            "Untrusted RSA signer");
        RuntimePinnedPrincipalVerifier wrongKid = new RuntimePinnedPrincipalVerifier(
            publicPem(identityKey), "some-other-key", ISSUER, AUDIENCE,
            Clock.systemUTC());
        rejects(() -> wrongKid.verifyBearerForRevocation("Bearer " + issued1),
            "Unpinned kid");
        RuntimePinnedPrincipalVerifier wrongIssuer = new RuntimePinnedPrincipalVerifier(
            publicPem(identityKey), KID, "untrusted-identity", AUDIENCE,
            Clock.systemUTC());
        rejects(() -> wrongIssuer.verifyBearerForRevocation("Bearer " + issued1),
            "Unpinned issuer");
        RuntimePinnedPrincipalVerifier wrongAudience = new RuntimePinnedPrincipalVerifier(
            publicPem(identityKey), KID, ISSUER, "unrelated-service",
            Clock.systemUTC());
        rejects(() -> wrongAudience.verifyBearerForRevocation("Bearer " + issued1),
            "Cross-service audience");

        int dot = issued1.lastIndexOf('.');
        require(dot > 0 && dot < issued1.length() - 1, "Malformed Identity JWT");
        int signatureIndex = dot + 1;
        char replacement = issued1.charAt(signatureIndex) == 'A' ? 'B' : 'A';
        String tampered = issued1.substring(0, signatureIndex) + replacement
            + issued1.substring(signatureIndex + 1);
        rejects(() -> verifier.verifyBearerForRevocation("Bearer " + tampered),
            "Tampered signed JWT");

        // Legacy Platform default HMAC must remain valid only for its own
        // verifier; Runtime's pinned-RSA verifier must refuse it unconditionally.
        String oldHmac = new JwtService(
            TEST_HMAC_KEY, 300, ISSUER, AUDIENCE).issue(account);
        rejects(() -> verifier.verifyBearerForRevocation("Bearer " + oldHmac),
            "Legacy HMAC confused with opt-in RS256");

        RuntimePinnedPrincipalVerifier expiredClock = new RuntimePinnedPrincipalVerifier(
            publicPem(identityKey), KID, ISSUER, AUDIENCE,
            Clock.fixed(Instant.now().plusSeconds(3600), ZoneOffset.UTC));
        rejects(() -> expiredClock.verifyBearerForRevocation("Bearer " + issued1),
            "Expired signed token still accepted using advanced trusted clock");

        // Never print a JWT, key material, internal JDBC connection, or
        // personally identifying account data to workflow logs.
        System.out.println(
            "IDENTITY_TO_RUNTIME_ACTUAL_JWT_AND_PINNED_VERIFIER_INTEROP_PASS");
        System.out.println(
            "IDENTITY_TO_RUNTIME_NO_B62_SESSION_OR_HTTP_AUTHORIZATION_NO_GO");
    }
}
