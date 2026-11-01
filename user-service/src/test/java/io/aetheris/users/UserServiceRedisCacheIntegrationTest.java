package io.aetheris.users;

import io.aetheris.users.dto.UserResponse;
import io.aetheris.users.events.UserEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * REAL disposable Redis TCP integration, not a mock Redis or production DB.
 * Checks the exact GenericJackson2JsonRedisSerializer configured by CacheConfig.
 * The cache key remains global: this test does NOT certify tenant isolation.
 */
@EnabledIfSystemProperty(named = "aetheris.redis.integration", matches = "true")
class UserServiceRedisCacheIntegrationTest {
    private static final String NAMESPACE = "aetheris-review-" + UUID.randomUUID();
    private static final int SYNTHETIC_PORT = 16389;

    private static AnnotationConfigApplicationContext context() {
        assertEquals("yes", System.getenv("AETHERIS_REDIS_SYNTHETIC_ONLY"),
            "Must opt into the dedicated disposable loopback Redis service");
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(IsolatedRedisConfig.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @Import(CacheConfig.class)
    static class IsolatedRedisConfig {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory("127.0.0.1", SYNTHETIC_PORT);
        }

        @Bean
        CacheManager cacheManager(RedisConnectionFactory factory,
                                  RedisCacheConfiguration actualProductionConfig) {
            // Add only a test-run namespace; retain production TTL and serializer.
            return RedisCacheManager.builder(factory).cacheDefaults(
                actualProductionConfig.computePrefixWith(
                    name -> NAMESPACE + ":" + name + "::")).build();
        }

        @Bean
        UserRepository repository() {
            return mock(UserRepository.class);
        }

        @Bean
        UserEventPublisher publisher() {
            return mock(UserEventPublisher.class);
        }

        @Bean
        UserService service(UserRepository repository, UserEventPublisher publisher) {
            return new UserService(repository, publisher);
        }
    }

    @Test
    void redisRoundTripMustPreserveUserRecordsAndNotAllowPersistentCallerPoisoning() {
        List<UserResponse> expected =
            List.of(new UserResponse(null, "Redis fixture", "redis-fixture@example.invalid"));

        try (AnnotationConfigApplicationContext firstContext = context()) {
            UserRepository repository = firstContext.getBean(UserRepository.class);
            when(repository.findAll()).thenReturn(
                List.of(new User("Redis fixture", "redis-fixture@example.invalid")));
            List<UserResponse> first = firstContext.getBean(UserService.class).findAll();
            assertEquals(expected, first);
            assertThrows(UnsupportedOperationException.class, () ->
                first.add(new UserResponse(999L, "forged", "forged@example.invalid")));
            verify(repository, times(1)).findAll();
        }

        // A completely new Spring context MUST hit Redis without touching the
        // repository. A serialized immutable Java list must deserialize cleanly.
        try (AnnotationConfigApplicationContext secondContext = context()) {
            UserRepository repository = secondContext.getBean(UserRepository.class);
            List<UserResponse> second = secondContext.getBean(UserService.class).findAll();
            assertEquals(expected, second);
            verifyNoInteractions(repository);
            // Redis may return a mutable COPY; any local tampering must not
            // write back into the Redis entry or poison a third context.
            try {
                second.add(new UserResponse(999L, "forged", "forged@example.invalid"));
            } catch (UnsupportedOperationException ignored) {
                // An immutable deserialized copy is also acceptable.
            }
        }

        try (AnnotationConfigApplicationContext thirdContext = context()) {
            UserRepository repository = thirdContext.getBean(UserRepository.class);
            assertEquals(expected, thirdContext.getBean(UserService.class).findAll());
            verifyNoInteractions(repository);
        }
    }
}
