package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses the actual Boot/Gateway context, not a YAML string parser.
 * No external service is contacted; the Gateway's live proxy path still needs
 * disposable Docker/Redis/upstream integration testing before release.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "aetheris.jwt.secret=synthetic-review-signing-key-with-more-than-32-bytes",
            "spring.data.redis.host=127.0.0.1",
            "spring.cloud.gateway.server.webflux.redis-route-definition-repository.enabled=false",
            "management.tracing.enabled=false",
            "management.otlp.tracing.export.enabled=false",
            "management.health.redis.enabled=false"
        })
class GatewayLiveRouteBindingTest {
    @Autowired
    private RouteLocator routeLocator;

    @Test
    void actualGatewayRouteLocatorBindsFiveProtectedServiceRoutes() {
        assertNotNull(routeLocator);
        List<Route> routes = routeLocator.getRoutes()
                .collectList().block(Duration.ofSeconds(15));
        assertNotNull(routes);
        assertEquals(5, routes.size(), "Gateway route list must not silently disappear after upgrade");
        assertEquals(Set.of("identity-service", "user-service-read", "user-service-write",
                "audit-service", "orchestrator-service"),
                routes.stream().map(Route::getId).collect(Collectors.toSet()));
        for (Route route : routes) {
            assertNotNull(route.getUri(), "Missing upstream for " + route.getId());
        }
    }
}
