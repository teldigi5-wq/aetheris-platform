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
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class IdentityRs256B60RotationIT {
    private static final Path ROOT=Path.of("..","identity-service","target","b60-rotation").toAbsolutePath().normalize();
    private static final String ISSUER="aetheris-identity", AUDIENCE="aetheris-api";
    private static String read(String name) throws Exception {
        Path p=ROOT.resolve(name);
        assertTrue(p.startsWith(ROOT) && Files.isRegularFile(p) && !Files.isSymbolicLink(p));
        assertTrue(Files.size(p)<8192);
        return Files.readString(p,StandardCharsets.US_ASCII).trim();
    }
    private static JwtAuthenticationFilter verifier(String priorPublic, String priorKid) throws Exception {
        return new JwtAuthenticationFilter("",ISSUER,AUDIENCE,"rs256",
            read("current-public-x509.b64"),"b60-current",priorPublic,priorKid,
            priorKid.isBlank() ? "" : Long.toString(java.time.Instant.now().plusSeconds(600).getEpochSecond()));
    }
    private static void check(JwtAuthenticationFilter filter, String jwt, boolean accepted) {
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/users")
            .header(HttpHeaders.AUTHORIZATION,"Bearer "+jwt)
            .header("X-Aetheris-User-Role","ADMIN").build());
        AtomicBoolean forwarded=new AtomicBoolean(false);
        GatewayFilterChain chain=e->{forwarded.set(true);return Mono.empty();};
        filter.filter(exchange,chain).block();
        assertEquals(accepted,forwarded.get());
        if(accepted){
            assertEquals("42",exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        }else{
            assertEquals(HttpStatus.UNAUTHORIZED,exchange.getResponse().getStatusCode());
            assertNull(exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        }
    }
    @Test
    void overlapRequiresMatchingActualSignatureAndKidBeforeRouting() throws Exception {
        assertEquals("B60_EPHEMERAL_PUBLIC_ROLLOVER_V1",read("test-only.marker"));
        var filter=verifier(read("previous-public-x509.b64"),"b60-previous");
        for(String accepted:new String[]{"current","previous"}) check(filter,read(accepted+".jwt"),true);
        for(String denied:new String[]{"relabel-previous","unknown-kid","untrusted-key",
                                       "foreign-issuer","workload-only"}) check(filter,read(denied+".jwt"),false);
    }
    @Test
    void oldKeyIsDeniedAfterPublicVerifierRetirement() throws Exception {
        var filter=verifier("","");
        check(filter,read("current.jwt"),true);
        check(filter,read("previous.jwt"),false);
    }
    @Test
    void invalidKeyOverlapConfigurationCannotStartGateway() throws Exception {
        String current=read("current-public-x509.b64"),prior=read("previous-public-x509.b64");
        assertThrows(IllegalArgumentException.class,()->verifier(prior,""));
        assertThrows(IllegalArgumentException.class,()->verifier("","b60-previous"));
        assertThrows(IllegalArgumentException.class,()->verifier(prior,"b60-current"));
        assertThrows(IllegalArgumentException.class,()->verifier(current,"b60-previous"));
        assertThrows(IllegalArgumentException.class,()->verifier("not-base64","b60-previous"));
        assertThrows(IllegalArgumentException.class,()->new JwtAuthenticationFilter(
            "test-only-HMAC-do-not-mix-strong-secret-64-bytes",ISSUER,AUDIENCE,"rs256",
            current,"b60-current",prior,"b60-previous"));
    }
}
