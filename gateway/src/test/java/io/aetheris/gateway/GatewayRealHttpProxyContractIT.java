package io.aetheris.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real Gateway Netty proxy to fixed local HTTP responder; NOT real User/Audit processes. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"aetheris.jwt.secret=test-only-direct-backend-auth-hmac-secret-at-least-32-bytes",
                "spring.data.redis.password=test-only-not-connected",
                "management.tracing.sampling.probability=0"})
@Import(GatewayRealHttpProxyContractIT.ContractRoutes.class)
class GatewayRealHttpProxyContractIT {
    static final AtomicInteger COUNT=new AtomicInteger();
    static final AtomicReference<String> ROLE=new AtomicReference<>();
    static final AtomicReference<String> UID=new AtomicReference<>();
    static final AtomicReference<String> BEARER=new AtomicReference<>();
    static final HttpServer TARGET=start();
    static final HttpClient CLIENT=HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${local.server.port}") int port;

    static HttpServer start() {
        try {
            var h=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            h.createContext("/",ex->{
                COUNT.incrementAndGet();
                ROLE.set(ex.getRequestHeaders().getFirst("X-Aetheris-User-Role"));
                UID.set(ex.getRequestHeaders().getFirst("X-Aetheris-User-Id"));
                BEARER.set(ex.getRequestHeaders().getFirst("Authorization"));
                byte[] bytes=("ISOLATED_PROXY_TARGET:"+ex.getRequestURI().getPath())
                    .getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("X-Aetheris-Proxy-Test-Only","true");
                ex.sendResponseHeaders(200,bytes.length);
                try(var output=ex.getResponseBody()){output.write(bytes);}
                ex.close();
            });
            h.start();
            return h;
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }

    @AfterAll static void close(){TARGET.stop(0);}

    @TestConfiguration(proxyBeanMethods=false)
    static class ContractRoutes {
        @Bean RouteLocator fixedLocalRoutes(RouteLocatorBuilder builder) {
            String endpoint="http://127.0.0.1:"+TARGET.getAddress().getPort();
            return builder.routes()
                .route("local-test-users",
                    r->r.order(-200).path("/api/users","/api/users/**").uri(endpoint))
                .route("local-test-audit",
                    r->r.order(-200).path("/api/events","/api/events/**").uri(endpoint))
                .build();
        }
    }

    String token(String name)throws Exception {
        Path root=Path.of("..","identity-service","target","identity-jwt-contract")
            .toAbsolutePath().normalize();
        assertEquals("IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1",
            Files.readString(root.resolve("test-only.marker")).trim());
        String jwt=Files.readString(root.resolve(name+".jwt")).trim();
        assertEquals(3,jwt.split("\\.").length);
        return jwt;
    }

    HttpResponse<String> send(String path,String method,String role,boolean forged)throws Exception {
        assertTrue(port>0);
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .timeout(Duration.ofSeconds(8));
        if(role!=null)b.header("Authorization","Bearer "+token(role));
        if(forged)b.header("X-Aetheris-User-Role","ADMIN")
            .header("X-Aetheris-User-Id","999999")
            .header("X-Aetheris-Scopes","services:write users:write");
        if(method.equals("POST"))b.POST(HttpRequest.BodyPublishers.noBody());
        else b.GET();
        return CLIENT.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }

    @Test void realGatewayHttpForwardsSignedIdentityAdminAndOverwritesForgedHeaders()
        throws Exception {
        int before=COUNT.get();
        var r=send("/api/users","GET","admin",true);
        assertEquals(200,r.statusCode());
        assertEquals("true",r.headers().firstValue("X-Aetheris-Proxy-Test-Only").orElse(null));
        assertEquals("ISOLATED_PROXY_TARGET:/api/users",r.body());
        assertEquals(before+1,COUNT.get());
        assertEquals("ADMIN",ROLE.get());
        assertEquals("42",UID.get());
        assertEquals("Bearer "+token("admin"),BEARER.get());
    }

    @Test void realGatewayHttpRejectsAllUnauthorizedTokensBeforeProxy()throws Exception {
        for(String candidate:new String[]{"anonymous","developer","consumer","expired","tampered"}) {
            int before=COUNT.get();
            var response=send("/api/users","GET",candidate.equals("anonymous")?null:candidate,true);
            int expected=candidate.equals("developer")||candidate.equals("consumer")?403:401;
            assertEquals(expected,response.statusCode(),candidate);
            assertEquals(before,COUNT.get(),"gateway forwarded "+candidate);
        }
    }

    @Test void actualAuditReadRoutesButAuditMutationCannotUseReadScope()throws Exception {
        int before=COUNT.get();
        var allowed=send("/api/events","GET","admin",true);
        assertEquals(200,allowed.statusCode());
        assertEquals("true",allowed.headers().firstValue("X-Aetheris-Proxy-Test-Only").orElse(null));
        assertEquals(before+1,COUNT.get());
        assertEquals("ADMIN",ROLE.get());
        assertEquals("42",UID.get());
        before=COUNT.get();
        var denied=send("/api/events","POST","admin",true);
        assertEquals(403,denied.statusCode());
        assertEquals(before,COUNT.get(),"unauthorized audit write reached local target");
    }
}
