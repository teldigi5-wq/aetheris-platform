package io.aetheris.audit.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B61: same live verifier instance refuses old tokens after its deadline. */
class IdentityRs256B61DeadlineIT {
    private static final Path ROOT=Path.of("..","identity-service","target","b60-rotation").toAbsolutePath().normalize();
    private static final String ISSUER="aetheris-identity", AUDIENCE="aetheris-api";
    private static String read(String name) throws Exception {
        Path p=ROOT.resolve(name);
        assertTrue(p.startsWith(ROOT) && Files.isRegularFile(p) && !Files.isSymbolicLink(p));
        assertTrue(Files.size(p)<8192);
        return Files.readString(p,StandardCharsets.US_ASCII).trim();
    }
    private static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> instant;
        AdjustableClock(Instant initial){ instant=new AtomicReference<>(initial); }
        void advanceTo(Instant value){ instant.set(value); }
        @Override public ZoneId getZone(){ return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone){ return this; }
        @Override public Instant instant(){ return instant.get(); }
    }
    private static DirectApiJwtFilter filter(Clock clock,String deadline) throws Exception {
        return new DirectApiJwtFilter("",ISSUER,AUDIENCE,"rs256",
            read("current-public-x509.b64"),"b60-current",
            read("previous-public-x509.b64"),"b60-previous",deadline,clock);
    }
    private static void check(DirectApiJwtFilter filter,String token,boolean allow) throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new io.aetheris.audit.AuditController(org.mockito.Mockito.mock(io.aetheris.audit.AuditEventStore.class))).addFilters(filter).build();
        var result=mvc.perform(get("/api/events").header("Authorization","Bearer "+read(token+".jwt")));
        if(allow) result.andExpect(status().isOk());
        else result.andExpect(status().isUnauthorized());
    }
    @Test
    void oldKeyStopsWorkingAtAbsoluteDeadlineWithoutRecreatingVerifier() throws Exception {
        assertEquals("B60_EPHEMERAL_PUBLIC_ROLLOVER_V1",read("test-only.marker"));
        Instant initial=Instant.now();
        AdjustableClock clock=new AdjustableClock(initial);
        Instant cutoff=initial.plusSeconds(20);
        DirectApiJwtFilter same=filter(clock,Long.toString(cutoff.getEpochSecond()));
        check(same,"previous",true); check(same,"current",true);
        clock.advanceTo(cutoff);
        check(same,"previous",false); check(same,"current",true);
        clock.advanceTo(cutoff.plusSeconds(80));
        check(same,"previous",false); check(same,"current",true);
    }
    @Test
    void improperlyConfiguredOrUnboundedOverlapFailsStartup() throws Exception {
        Instant now=Instant.now();
        String valid=Long.toString(now.plusSeconds(120).getEpochSecond());
        assertThrows(IllegalArgumentException.class,()->filter(Clock.systemUTC(),""));
        assertThrows(IllegalArgumentException.class,()->filter(Clock.systemUTC(),"bad-time"));
        assertThrows(IllegalArgumentException.class,()->filter(Clock.systemUTC(),Long.toString(now.plusSeconds(7200).getEpochSecond())));
        assertNotNull(filter(Clock.systemUTC(),valid));
        assertThrows(IllegalArgumentException.class,()->new DirectApiJwtFilter(
            "synthetic-32byte-hmac-do-not-mix",ISSUER,AUDIENCE,"hmac","","",
            "","","1730000000",Clock.systemUTC()));
    }
    @Test
    void staleDeadlineKeepsNewKeyAvailableButRejectsPrevious() throws Exception {
        Instant now=Instant.now();
        DirectApiJwtFilter filter=filter(Clock.systemUTC(),Long.toString(now.minusSeconds(2).getEpochSecond()));
        check(filter,"current",true);
        check(filter,"previous",false);
    }

    @Test
    void tokenIssueAndExpiryChecksUseSameInjectedClockAsKeyRetirement() throws Exception {
        // Only ephemeral Identity-issued RSA JWT fixtures are used: no real signing keys.
        Instant wallNow = Instant.now();
        AdjustableClock clock = new AdjustableClock(wallNow.minusSeconds(180));
        Instant cutoff = wallNow.plusSeconds(600);
        DirectApiJwtFilter same =
            filter(clock, Long.toString(cutoff.getEpochSecond()));

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

    @Test
    void actualIdentitySignedAccessTokensAreBoundedToOneHour() throws Exception {
        // No unsigned claim rewriting; source tokens come from real, ephemeral
        // Identity signing keys kept only inside synthetic test memory.
        Instant now = Instant.now();
        DirectApiJwtFilter same = filter(Clock.systemUTC(),
            Long.toString(now.plusSeconds(300).getEpochSecond()));
        check(same, "current", true);
        check(same, "previous", true);
        check(same, "max-lifetime", true);      // exactly 3600 seconds
        check(same, "overlong-lifetime", false); // signed 3601-second validity
        check(same, "zero-lifetime", false);     // exp equals iat
    }

    @Test
    void jwtParserUsesVerifierClockForExactNotBeforeAndExpiredBoundaries() throws Exception {
        // Exact time comes from the ephemeral Identity-signed fixture, not
        // test-machine wall-clock progress or any untrusted request header.
        Instant nbf = Instant.ofEpochSecond(Long.parseLong(read("future-nbf.epoch")));
        AdjustableClock clock = new AdjustableClock(nbf.minusSeconds(1));
        DirectApiJwtFilter same = filter(clock,
            Long.toString(nbf.plusSeconds(600).getEpochSecond()));

        // Strict zero JJWT parser skew: 1s early is denied.
        check(same, "future-nbf", false);
        clock.advanceTo(nbf);
        check(same, "future-nbf", true);

        // Fixture expiry is nbf - 300s + 1200s. Past it, deny in
        // the SAME verifier regardless of actual CI host wall time.
        clock.advanceTo(nbf.plusSeconds(901));
        check(same, "future-nbf", false);
    }
}
