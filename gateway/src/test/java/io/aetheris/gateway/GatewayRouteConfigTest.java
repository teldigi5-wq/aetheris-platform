package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class GatewayRouteConfigTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object source, String key) {
        assertInstanceOf(Map.class, source);
        Object child = ((Map<String, Object>)source).get(key);
        assertInstanceOf(Map.class, child, "Missing Spring Gateway config: " + key);
        return (Map<String, Object>)child;
    }

    @Test
    @SuppressWarnings("unchecked")
    void gatewayFiveZeroRoutesStillExistAtSupportedWebFluxPrefix() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertNotNull(input);
            Map<String, Object> root = new Yaml().load(input);
            Map<String, Object> gateway = map(map(root, "spring"), "cloud");
            gateway = map(gateway, "gateway");
            assertFalse(gateway.containsKey("routes"), "Legacy route property used");
            Map<String, Object> webflux = map(map(gateway, "server"), "webflux");
            assertTrue(webflux.containsKey("globalcors"));
            assertTrue(webflux.containsKey("httpclient"));
            Object raw = webflux.get("routes");
            assertInstanceOf(List.class, raw);
            List<Map<String, Object>> routes = (List<Map<String, Object>>)raw;
            assertEquals(5, routes.size());
            assertEquals(Set.of("identity-service", "user-service-read", "user-service-write",
                "audit-service", "orchestrator-service"),
                routes.stream().map(item -> (String)item.get("id")).collect(Collectors.toSet()));
            for (Map<String, Object> route : routes) {
                assertTrue(((List<?>)route.get("predicates")).stream()
                    .anyMatch(x -> String.valueOf(x).startsWith("Path=/api/")));
                assertTrue(((List<?>)route.get("filters")).stream()
                    .map(String::valueOf).anyMatch(x -> x.contains("RequestRateLimiter")));
            }
        }
    }
}
