package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * I11: the S0-E6 Identity components must be constructible by Spring itself, not only
 * by tests calling `new`. RuntimeAudienceTokenIssuer has two constructors, so Spring
 * needs the @Autowired marker; without it Identity fails at startup.
 */
class RuntimeAudienceSpringWiringTest {

    @Test
    void springBuildsTheDormantIssuerAndWorkloadPolicyWithDefaultConfiguration() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            context.register(RuntimeAudienceTokenIssuer.class, RuntimeExchangeGatewayWorkloadPolicy.class);
            context.refresh();
            assertNotNull(context.getBean(RuntimeAudienceTokenIssuer.class));
            assertNotNull(context.getBean(RuntimeExchangeGatewayWorkloadPolicy.class));
        }
    }

    @Test
    void partialOperatorConfigurationStillFailsClosedThroughSpring() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("aetheris.runtime-token.key-id", "only-a-kid"));
            context.register(RuntimeAudienceTokenIssuer.class);
            assertThrows(Exception.class, context::refresh);
        }
    }
}
