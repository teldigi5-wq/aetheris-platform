package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StagedMediaJobServiceTest {
    private static final StagedMediaJobService.MediaRequest IMAGE =
            new StagedMediaJobService.MediaRequest("IMAGE", "test sunrise image", "", 512, 512);

    private static final class AdjustableClock extends Clock {
        private Instant current = Instant.parse("2026-10-11T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }

        void advanceSeconds(long seconds) { current = current.plusSeconds(seconds); }
    }

    @Test
    void disabledIsDefaultAndNeverStagesJobs() {
        StagedMediaJobService service = new StagedMediaJobService(false);
        ResponseStatusException refusal = assertThrows(ResponseStatusException.class,
                () -> service.submit(42L, UUID.randomUUID().toString(), IMAGE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, refusal.getStatusCode());
    }

    @Test
    void ownerFromVerifiedUidAndNoPromptInResponse() {
        StagedMediaJobService service = new StagedMediaJobService(true);
        UUID requestId = UUID.randomUUID();
        var created = service.submit(42L, requestId.toString(), IMAGE);
        assertEquals("STAGED_NOT_EXECUTED", created.state());
        assertEquals("BLOCKED_PENDING_HARDWARE", created.physicalPcStatus());
        assertFalse(created.gpuJobStarted());
        assertFalse(created.toString().contains(IMAGE.prompt()));
        assertEquals(created, service.get(42L, created.id()));
    }

    @Test
    void idempotentReplayStableAndConflictingPayloadRejected() {
        StagedMediaJobService service = new StagedMediaJobService(true);
        String key = UUID.randomUUID().toString();
        var first = service.submit(42L, key, IMAGE);
        var repeated = service.submit(42L, key, IMAGE);
        assertEquals(first, repeated);
        var changed = new StagedMediaJobService.MediaRequest("IMAGE", "altered payload", "", 512, 512);
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.submit(42L, key, changed));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        var other = service.submit(43L, key, changed);
        assertNotEquals(first.id(), other.id());
    }

    @Test
    void crossOwnerReadsAndDeletesReturnSameNotFoundAsUnknown() {
        StagedMediaJobService service = new StagedMediaJobService(true);
        var created = service.submit(42L, UUID.randomUUID().toString(), IMAGE);
        for (long otherUid : new long[]{43L, 999L}) {
            ResponseStatusException read = assertThrows(ResponseStatusException.class,
                    () -> service.get(otherUid, created.id()));
            assertEquals(HttpStatus.NOT_FOUND, read.getStatusCode());
            ResponseStatusException del = assertThrows(ResponseStatusException.class,
                    () -> service.remove(otherUid, created.id()));
            assertEquals(HttpStatus.NOT_FOUND, del.getStatusCode());
        }
        assertEquals(created, service.get(42L, created.id()));
        service.remove(42L, created.id());
        ResponseStatusException gone = assertThrows(ResponseStatusException.class,
                () -> service.get(42L, created.id()));
        assertEquals(HttpStatus.NOT_FOUND, gone.getStatusCode());
    }

    @Test
    void noOwnerSuppliedByUnsignedHeaderOrDirectControllerMethod() {
        StagedMediaJobService service = new StagedMediaJobService(true);
        MediaStagingController controller = new MediaStagingController(service);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/media/capabilities")
                .header("X-Aetheris-Owner-Id", "42")
                .header("X-Aetheris-User-Id", "42").build());
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> controller.capabilities(exchange));
        assertEquals(HttpStatus.UNAUTHORIZED, missing.getStatusCode());
        exchange.getAttributes().put(MediaJobAuthWebFilter.VERIFIED_OWNER_ATTRIBUTE, 42L);
        assertEquals("NOT_WIRED", controller.capabilities(exchange).get("imageGenerationState"));
    }

    @Test
    void invalidAndDangerousMediaPayloadsFailClosed() {
        StagedMediaJobService service = new StagedMediaJobService(true);
        var invalid = new StagedMediaJobService.MediaRequest[]{
                null,
                new StagedMediaJobService.MediaRequest("VIDEO", "test", "", 512, 512),
                new StagedMediaJobService.MediaRequest("IMAGE", "", "", 512, 512),
                new StagedMediaJobService.MediaRequest("IMAGE", "x".repeat(1501), "", 512, 512),
                new StagedMediaJobService.MediaRequest("IMAGE", "image", "", 8192, 8192),
                new StagedMediaJobService.MediaRequest("IMAGE", "image", "", 512, 1024),
                new StagedMediaJobService.MediaRequest("IMAGE", "image", "n".repeat(501), 512, 512),
        };
        for (var request : invalid) {
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class,
                            () -> service.submit(42L, UUID.randomUUID().toString(), request)).getStatusCode());
        }
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class,
                        () -> service.submit(42L, "non-uuid-key", IMAGE)).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class,
                        () -> service.submit(-1L, UUID.randomUUID().toString(), IMAGE)).getStatusCode());
    }

    @Test
    void memoryQuotaAndExpiryBounded() {
        AdjustableClock clock = new AdjustableClock();
        StagedMediaJobService service = new StagedMediaJobService(true, clock);
        var first = service.submit(42L, UUID.randomUUID().toString(), IMAGE);
        for (int i = 1; i < 8; i++) service.submit(42L, UUID.randomUUID().toString(), IMAGE);
        var full = assertThrows(ResponseStatusException.class,
                () -> service.submit(42L, UUID.randomUUID().toString(), IMAGE));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, full.getStatusCode());

        clock.advanceSeconds(601);
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.get(42L, first.id())).getStatusCode());
        var fresh = service.submit(42L, UUID.randomUUID().toString(), IMAGE);
        assertNotEquals(first.id(), fresh.id());
        assertFalse(fresh.gpuJobStarted());
    }
}
