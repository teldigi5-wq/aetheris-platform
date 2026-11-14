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

/** True signed Identity RSA contract through real Gateway JwtAuthenticationFilter. */
class IdentityRs256B59VerifierIT {
    private static final Path ROOT=Path.of("..","identity-service","target","b59-rsa-contract").toAbsolutePath().normalize();
    private static final String ISSUER="aetheris-identity",AUDIENCE="aetheris-api",KID="b59-ephemeral-a";

    private static String read(String name) throws Exception {
        Path path=ROOT.resolve(name);
        assertTrue(path.startsWith(ROOT) && Files.isRegularFile(path) && !Files.isSymbolicLink(path));
        assertTrue(Files.size(path)<=8192);
        return Files.readString(path,StandardCharsets.US_ASCII).trim();
    }

    private static void check(JwtAuthenticationFilter filter, String token, boolean shouldPass) {
        var exchange=MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users")
                .header(HttpHeaders.AUTHORIZATION,"Bearer "+token)
                .header("X-Aetheris-User-Role","ADMIN").build());
        AtomicBoolean forwarded=new AtomicBoolean(false);
        GatewayFilterChain chain=actual->{forwarded.set(true);return Mono.empty();};
        filter.filter(exchange,chain).block();
        assertEquals(shouldPass,forwarded.get());
        if (shouldPass) {
            assertNotEquals(HttpStatus.UNAUTHORIZED,exchange.getResponse().getStatusCode());
            assertEquals("42",exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        } else {
            assertEquals(HttpStatus.UNAUTHORIZED,exchange.getResponse().getStatusCode());
            assertNull(exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        }
    }

    @Test
    void publicVerifierAcceptsIdentityRsaAndRejectsSevenSignedAttackVariants() throws Exception {
        assertEquals("B59_EPHEMERAL_IDENTITY_RSA_V1",read("test-only.marker"));
        var filter=new JwtAuthenticationFilter("",ISSUER,AUDIENCE,"rs256",read("public-x509.b64"),KID);
        check(filter,read("admin.jwt"),true);
        for (String attack: new String[]{"unknown-kid","wrong-issuer","wrong-audience",
                                        "wrong-signature","expired","legacy-hmac","tampered"}) {
            check(filter,read(attack+".jwt"),false);
        }
    }

    @Test
    void missingRsaPublicKeyWrongModeAndMixedHmacConfigFailClosed() throws Exception {
        String publicKey=read("public-x509.b64");
        assertThrows(IllegalArgumentException.class,()->
            new JwtAuthenticationFilter("existing-hmac-secret-32-bytes-at-least",
                ISSUER,AUDIENCE,"rs256",publicKey,KID));
        assertThrows(IllegalArgumentException.class,()->
            new JwtAuthenticationFilter("",ISSUER,AUDIENCE,"rs256","",KID));
        assertThrows(IllegalArgumentException.class,()->
            new JwtAuthenticationFilter("",ISSUER,AUDIENCE,"rs256",publicKey,""));
        assertThrows(IllegalArgumentException.class,()->
            new JwtAuthenticationFilter("",ISSUER,AUDIENCE,"unsupported",publicKey,KID));
        assertThrows(IllegalArgumentException.class,()->
            new JwtAuthenticationFilter("",ISSUER,AUDIENCE,"hmac",publicKey,KID));
    }
}
