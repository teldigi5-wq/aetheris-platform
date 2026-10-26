package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** B61: expiration is checked per request in the SAME Gateway filter instance. */
class IdentityRs256B61DeadlineIT {
    private static final Path ROOT=Path.of("..","identity-service","target","b60-rotation").toAbsolutePath().normalize();
    private static final String ISSUER="aetheris-identity",AUDIENCE="aetheris-api";
    private static String read(String name) throws Exception {
        Path p=ROOT.resolve(name);
        assertTrue(p.startsWith(ROOT) && Files.isRegularFile(p) && !Files.isSymbolicLink(p));
        assertTrue(Files.size(p)<8192);
        return Files.readString(p,StandardCharsets.US_ASCII).trim();
    }
    private static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> now;
        AdjustableClock(Instant initial){now=new AtomicReference<>(initial);}
        void advanceTo(Instant next){now.set(next);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now.get();}
    }
    private static JwtAuthenticationFilter verifier(Clock clock,String deadline) throws Exception {
        return new JwtAuthenticationFilter("",ISSUER,AUDIENCE,"rs256",
            read("current-public-x509.b64"),"b60-current",
            read("previous-public-x509.b64"),"b60-previous",deadline,clock);
    }
    private static void check(JwtAuthenticationFilter filter,String token,boolean allow) throws Exception {
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/users")
            .header(HttpHeaders.AUTHORIZATION,"Bearer "+read(token+".jwt")).build());
        AtomicBoolean forwarded=new AtomicBoolean(false);
        GatewayFilterChain chain=e->{forwarded.set(true);return Mono.empty();};
        filter.filter(exchange,chain).block();
        assertEquals(allow,forwarded.get());
        if(!allow) assertEquals(HttpStatus.UNAUTHORIZED,exchange.getResponse().getStatusCode());
        else assertEquals("42",exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
    }
    @Test
    void priorKeyDiesExactlyAtUtcCutoffWithoutFilterRestart() throws Exception {
        assertEquals("B60_EPHEMERAL_PUBLIC_ROLLOVER_V1",read("test-only.marker"));
        Instant start=Instant.now();
        AdjustableClock clock=new AdjustableClock(start);
        Instant cutoff=start.plusSeconds(20);
        JwtAuthenticationFilter same=verifier(clock,Long.toString(cutoff.getEpochSecond()));
        check(same,"previous",true);check(same,"current",true);
        clock.advanceTo(cutoff);
        check(same,"previous",false);check(same,"current",true);
        clock.advanceTo(cutoff.plusSeconds(80));
        check(same,"previous",false);check(same,"current",true);
    }
    @Test
    void refusedMissingInvalidFarFutureAndHmacMixedDeadline() throws Exception {
        Instant now=Instant.now();
        assertThrows(IllegalArgumentException.class,()->verifier(Clock.systemUTC(),""));
        assertThrows(IllegalArgumentException.class,()->verifier(Clock.systemUTC(),"invalid"));
        assertThrows(IllegalArgumentException.class,()->verifier(Clock.systemUTC(),Long.toString(now.plusSeconds(7200).getEpochSecond())));
        assertNotNull(verifier(Clock.systemUTC(),Long.toString(now.plusSeconds(120).getEpochSecond())));
        assertThrows(IllegalArgumentException.class,()->new JwtAuthenticationFilter(
            "synthetic-32byte-hmac-do-not-mix",ISSUER,AUDIENCE,"hmac","","",
            "","","1730000000",Clock.systemUTC()));
    }
    @Test
    void expiredConfiguredCutoffDeniesOldButKeepsCurrentTokenUsable() throws Exception {
        var same=verifier(Clock.systemUTC(),Long.toString(Instant.now().minusSeconds(2).getEpochSecond()));
        check(same,"previous",false);check(same,"current",true);
    }

    @Test
    void tokenIssueAndExpiryChecksUseSameInjectedClockAsKeyRetirement() throws Exception {
        // Only ephemeral Identity-issued RSA JWT fixtures are used: no real signing keys.
        Instant wallNow = Instant.now();
        AdjustableClock clock = new AdjustableClock(wallNow.minusSeconds(180));
        Instant cutoff = wallNow.plusSeconds(600);
        JwtAuthenticationFilter same =
            verifier(clock, Long.toString(cutoff.getEpochSecond()));

        // Signed tokens are valid in real wall time, but their iat is too far
        // into this verifier's deliberately lagging test clock (>30s).
        check(same, "current", false);
        check(same, "previous", false);

        clock.advanceTo(wallNow);
        check(same, "current", true);
        check(same, "previous", true);

        // B61's absolute key deadline remains separate from JWT expiration.
        clock.advanceTo(cutoff);
        check(same, "previous", false);
        check(same, "current", true);

        // Current-key tokens cannot outlive their signed expiration when
        // the injected clock advances; verification uses this same clock.
        clock.advanceTo(wallNow.plusSeconds(1500));
        check(same, "current", false);
    }
}
