package io.aetheris.users;

import io.aetheris.users.dto.UserResponse;
import io.aetheris.users.events.UserEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * REAL disposable Redis TCP integration, not a mock Redis or production DB.
 * Uses CacheConfig's actual production RedisCacheManager and typed JSON serializers.
 * The cache key remains global: this test does NOT certify tenant isolation.
 */
@EnabledIfSystemProperty(named = "aetheris.redis.integration", matches = "true")
class UserServiceRedisCacheIntegrationTest {
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
    void singleUserCacheMustUseTypedRecordAndUnknownCachesMustBeDisabled() {
        UserResponse expected = new UserResponse(null, "Single Redis", "single@example.invalid");
        try (AnnotationConfigApplicationContext firstContext = context()) {
            assertNull(firstContext.getBean(org.springframework.cache.CacheManager.class)
                .getCache("arbitrary-unreviewed-cache"),
                "Only approved typed cache names may be created");
            UserRepository repository = firstContext.getBean(UserRepository.class);
            when(repository.findById(7L)).thenReturn(
                Optional.of(new User("Single Redis", "single@example.invalid")));
            assertEquals(expected, firstContext.getBean(UserService.class).findById(7L));
            verify(repository, times(1)).findById(7L);
        }
        try (AnnotationConfigApplicationContext secondContext = context()) {
            UserRepository repository = secondContext.getBean(UserRepository.class);
            assertEquals(expected, secondContext.getBean(UserService.class).findById(7L));
            verifyNoInteractions(repository);
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

        // A completely new Spring context MUST hit real Redis without touching the
        // repository. The immutable source list must serialize and deserialize.
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
