package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuntimeAudienceSessionExchangeServiceTest {
    private static final String KID = "e6f-test-only-key";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final Clock CLOCK = Clock.systemUTC();
    private static final String VALID_SHAPE =
        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    private static IdentityAccount account(Role role, long id) {
        var a = new IdentityAccount("Fixture", "synthetic@example.invalid",
            "unusable-hash", role);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private static KeyPair rsa() {
        try {
            var g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot build test-only key",ex);
        }
    }

    private static RuntimeAudienceTokenIssuer configured(KeyPair pair) {
        return new RuntimeAudienceTokenIssuer(new MockEnvironment()
            .withProperty("aetheris.runtime-token.pkcs8-base64",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()))
            .withProperty("aetheris.runtime-token.key-id",KID)
            .withProperty("aetheris.runtime-token.issuer",ISSUER)
            .withProperty("aetheris.runtime-token.audience",AUDIENCE), CLOCK);
    }

    @Test
    void absentSigningKeyFailsBeforeConsumingRefreshCredential() {
        RefreshTokenService sessions = mock(RefreshTokenService.class);
        IdentityAccountRepository accounts = mock(IdentityAccountRepository.class);
        var disabled = new RuntimeAudienceTokenIssuer(new MockEnvironment(), CLOCK);
        var exchange = new RuntimeAudienceSessionExchangeService(
            sessions, accounts, disabled);
        assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> exchange.exchangeUsingSingleUseRefreshCredential(VALID_SHAPE));
        verifyNoInteractions(sessions, accounts);
    }

    @Test
    void malformedOrMissingCredentialNeverTouchesDatabase() {
        var issuer = mock(RuntimeAudienceTokenIssuer.class);
        when(issuer.isConfigured()).thenReturn(true);
        var sessions = mock(RefreshTokenService.class);
        var accounts = mock(IdentityAccountRepository.class);
        var exchange = new RuntimeAudienceSessionExchangeService(
            sessions, accounts, issuer);
        for (String raw : new String[]{null, "", "guess", "a".repeat(42),
                "a".repeat(44), "a".repeat(42)+"!", "a".repeat(41)+"\n"}) {
            assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
                () -> exchange.exchangeUsingSingleUseRefreshCredential(raw));
        }
        verifyNoInteractions(sessions, accounts);
        verify(issuer,never()).issueForVerifiedExchange(any());
    }

    @Test
    void revokedExpiredOrRacingRefreshCredentialsNeverProduceRuntimeJwt() {
        var issuer = mock(RuntimeAudienceTokenIssuer.class);
        when(issuer.isConfigured()).thenReturn(true);
        var sessions = mock(RefreshTokenService.class);
        var accounts = mock(IdentityAccountRepository.class);
        when(sessions.rotate(VALID_SHAPE))
            .thenThrow(new RefreshTokenService.InvalidRefreshTokenException());
        var exchange = new RuntimeAudienceSessionExchangeService(
            sessions, accounts, issuer);
        assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> exchange.exchangeUsingSingleUseRefreshCredential(VALID_SHAPE));
        verifyNoInteractions(accounts);
        verify(issuer,never()).issueForVerifiedExchange(any());
    }

    @Test
    void staleDeletedOrMismatchedIdentityFailsAfterAtomicRotation() {
        var issuer = mock(RuntimeAudienceTokenIssuer.class);
        when(issuer.isConfigured()).thenReturn(true);
        var sessions = mock(RefreshTokenService.class);
        var accounts = mock(IdentityAccountRepository.class);
        var session = account(Role.DEVELOPER, 7);
        var replacement = new RefreshTokenService.IssuedRefreshToken("replacement",300);
        when(sessions.rotate(VALID_SHAPE)).thenReturn(
            new RefreshTokenService.RotationResult(session, replacement));
        var exchange = new RuntimeAudienceSessionExchangeService(
            sessions, accounts, issuer);

        when(accounts.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> exchange.exchangeUsingSingleUseRefreshCredential(VALID_SHAPE));

        when(accounts.findById(7L)).thenReturn(Optional.of(account(Role.ADMIN,8)));
        assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> exchange.exchangeUsingSingleUseRefreshCredential(VALID_SHAPE));
        verify(issuer,never()).issueForVerifiedExchange(any());
    }

    @Test
    void freshDatabaseRoleIsUsedRatherThanAnyStaleTokenAccountRole() {
        KeyPair key = rsa();
        var issuer = configured(key);
        var sessions = mock(RefreshTokenService.class);
        var accounts = mock(IdentityAccountRepository.class);
        var sessionAccount = account(Role.ADMIN,42L); // potentially stale
        var freshAccount = account(Role.API_CONSUMER,42L);
        var replacement = new RefreshTokenService.IssuedRefreshToken("new-session-raw",300L);
        when(sessions.rotate(VALID_SHAPE)).thenReturn(
            new RefreshTokenService.RotationResult(sessionAccount, replacement));
        when(accounts.findById(42L)).thenReturn(Optional.of(freshAccount));
        var exchange = new RuntimeAudienceSessionExchangeService(
            sessions, accounts, issuer);

        var result = exchange.exchangeUsingSingleUseRefreshCredential(VALID_SHAPE);
        assertSame(replacement,result.rotatedRefreshToken());
        var claims = Jwts.parser().verifyWith(key.getPublic()).build()
            .parseSignedClaims(result.runtimeAccessToken()).getPayload();
        assertEquals(42L,((Number)claims.get("uid")).longValue());
        assertEquals("API_CONSUMER",claims.get("role",String.class));
        assertEquals(Set.of("orchestrator:read"),
            Set.copyOf((java.util.List<?>)claims.get("scopes",java.util.List.class)));
        verify(sessions).rotate(VALID_SHAPE);
        verify(accounts).findById(42L);
    }

    @Test
    void actualRefreshRotationCannotExchangeTheSameCredentialTwice() {
        KeyPair key = rsa();
        var issuer = configured(key);
        var repository = mock(RefreshTokenRepository.class);
        Map<String,RefreshToken> stored = new HashMap<>();
        when(repository.save(any(RefreshToken.class))).thenAnswer(call -> {
            RefreshToken token=call.getArgument(0);
            stored.put(token.getTokenHash(),token);
            return token;
        });
        when(repository.findByTokenHash(anyString())).thenAnswer(call -> {
            String hash=call.getArgument(0);
            return Optional.ofNullable(stored.get(hash));
        });
        when(repository.revokeIfActive(anyString(),any(Instant.class))).thenAnswer(call -> {
            String hash=call.getArgument(0);
            Instant now=call.getArgument(1);
            synchronized(stored) {
                RefreshToken token=stored.get(hash);
                if (token==null || token.isRevoked()
                        || !token.getExpiresAt().isAfter(now)) return 0;
                token.revoke(); // simulate repository's atomic UPDATE state
                return 1;
            }
        });
        var sessions=new RefreshTokenService(repository,604800L);
        var accounts=mock(IdentityAccountRepository.class);
        var dbAccount=account(Role.DEVELOPER,101L);
        when(accounts.findById(101L)).thenReturn(Optional.of(dbAccount));
        String first=sessions.issue(dbAccount).token();
        var exchange = new RuntimeAudienceSessionExchangeService(
            sessions,accounts,issuer);
        var result=exchange.exchangeUsingSingleUseRefreshCredential(first);
        assertNotNull(result.runtimeAccessToken());
        assertNotEquals(first,result.rotatedRefreshToken().token());
        assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> exchange.exchangeUsingSingleUseRefreshCredential(first));
        var next=exchange.exchangeUsingSingleUseRefreshCredential(
            result.rotatedRefreshToken().token());
        assertNotEquals(result.runtimeAccessToken(),next.runtimeAccessToken());
        verify(repository,times(2)).revokeIfActive(anyString(),any(Instant.class));
    }

    @Test
    void signingFailureDoesNotProduceAnyCredentialResponse() {
        var issuer=mock(RuntimeAudienceTokenIssuer.class);
        when(issuer.isConfigured()).thenReturn(true);
        var sessions=mock(RefreshTokenService.class);
        var accounts=mock(IdentityAccountRepository.class);
        var account=account(Role.DEVELOPER,10L);
        when(sessions.rotate(VALID_SHAPE)).thenReturn(
            new RefreshTokenService.RotationResult(account,
                new RefreshTokenService.IssuedRefreshToken("rotated",100)));
        when(accounts.findById(10L)).thenReturn(Optional.of(account));
        when(issuer.issueForVerifiedExchange(account))
            .thenThrow(new IllegalStateException("synthetic signing failure"));
        var service=new RuntimeAudienceSessionExchangeService(sessions,accounts,issuer);
        assertThrows(IllegalStateException.class,
            () -> service.exchangeUsingSingleUseRefreshCredential(VALID_SHAPE));
        assertNotNull(RuntimeAudienceSessionExchangeService.class
            .getDeclaredAnnotation(org.springframework.stereotype.Service.class));
        var method = java.util.Arrays.stream(RuntimeAudienceSessionExchangeService.class
            .getDeclaredMethods())
            .filter(m->m.getName().equals("exchangeUsingSingleUseRefreshCredential"))
            .findFirst().orElseThrow();
        var transaction=method.getAnnotation(
            org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(transaction, "Outer transaction must roll back on signing failure");
        assertArrayEquals(new Class[]{Exception.class},transaction.rollbackFor());
    }
}
