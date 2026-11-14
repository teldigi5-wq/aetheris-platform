package io.aetheris.identity;

import org.junit.jupiter.api.RepeatedTest;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * I6 (B62/S0 readiness §5): concurrent replay of ONE refresh token.
 *
 * Many threads present the same token at the same instant. The repository's
 * conditional UPDATE is modelled as a single atomic compare-and-set (the property
 * PostgreSQL provides for {@code update ... where revoked = false}). Exactly one
 * rotation may succeed and exactly one replacement may be issued; every other
 * caller must get InvalidRefreshTokenException and mint nothing.
 *
 * Scope: this proves the service's claim-before-issue ordering under real thread
 * concurrency. It is not evidence about PostgreSQL itself; that remains the
 * runtime proof's job.
 */
class RefreshRotationConcurrencyTest {
    private static final int CALLERS = 16;

    @RepeatedTest(5)
    void exactlyOneConcurrentRotationOfTheSameTokenSucceeds() throws Exception {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("I6 Synthetic", "i6@example.invalid", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 3600L);
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken original = service.issue(account);
        ArgumentCaptor<RefreshToken> created = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(created.capture());
        RefreshToken persisted = created.getValue();
        clearInvocations(repository);

        AtomicBoolean active = new AtomicBoolean(true);
        when(repository.findByTokenHash(persisted.getTokenHash())).thenReturn(Optional.of(persisted));
        when(repository.revokeIfActive(eq(persisted.getTokenHash()), any(Instant.class)))
                .thenAnswer(invocation -> active.compareAndSet(true, false) ? 1 : 0);

        CountDownLatch ready = new CountDownLatch(CALLERS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
        try {
            List<Future<RefreshTokenService.RotationResult>> results = new ArrayList<>();
            for (int i = 0; i < CALLERS; i++) {
                Callable<RefreshTokenService.RotationResult> call = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return service.rotate(original.token());
                    } catch (RefreshTokenService.InvalidRefreshTokenException expected) {
                        rejected.incrementAndGet();
                        return null;
                    }
                };
                results.add(pool.submit(call));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();

            List<RefreshTokenService.RotationResult> winners = new ArrayList<>();
            for (Future<RefreshTokenService.RotationResult> result : results) {
                RefreshTokenService.RotationResult value = result.get(10, TimeUnit.SECONDS);
                if (value != null) winners.add(value);
            }

            assertEquals(1, winners.size(), "exactly one concurrent rotation wins");
            assertEquals(CALLERS - 1, rejected.get(), "every other replay is rejected");
            assertNotEquals(original.token(), winners.get(0).refreshToken().token());
            verify(repository, times(1)).save(any(RefreshToken.class)); // one replacement only
            verify(repository, times(CALLERS)).revokeIfActive(eq(persisted.getTokenHash()), any(Instant.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(3)
    void replayAfterSuccessfulRotationIsRejected() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("I6 Synthetic", "i6-replay@example.invalid", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 3600L);
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken original = service.issue(account);
        ArgumentCaptor<RefreshToken> created = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(created.capture());
        RefreshToken persisted = created.getValue();

        AtomicBoolean active = new AtomicBoolean(true);
        when(repository.findByTokenHash(persisted.getTokenHash())).thenReturn(Optional.of(persisted));
        when(repository.revokeIfActive(eq(persisted.getTokenHash()), any(Instant.class)))
                .thenAnswer(invocation -> active.compareAndSet(true, false) ? 1 : 0);

        assertNotNull(service.rotate(original.token()));
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate(original.token()));
    }
}
