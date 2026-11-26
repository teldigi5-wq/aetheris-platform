package io.aetheris.users;

import io.aetheris.users.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Exercises the PRODUCTION Redis value serializer from {@link CacheConfig} without a Redis
 * server: what the cache writes for usersList/userById must read back as the same types,
 * otherwise every cache hit fails at runtime (the in-memory cache tests cannot see this).
 */
class CacheConfigRedisRoundTripTest {

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(Object value) {
        RedisCacheConfiguration configuration = new CacheConfig().redisCacheConfiguration();
        SerializationPair<Object> pair = configuration.getValueSerializationPair();
        ByteBuffer written = pair.write(value);
        return (T) pair.read(written);
    }

    @Test
    void singleUserReadsBackAsUserResponse() {
        UserResponse original = new UserResponse(7L, "Poojana", "poojana@example.com");
        Object cached = roundTrip(original);
        assertInstanceOf(UserResponse.class, cached,
                () -> "userById cache hit returned " + cached.getClass().getName());
        assertEquals(original, cached);
    }

    @Test
    void userListReadsBackAsListOfUserResponse() {
        List<UserResponse> original = new ArrayList<>(List.of(
                new UserResponse(1L, "A", "a@example.com"),
                new UserResponse(2L, "B", "b@example.com")));
        Object cached = roundTrip(original);
        assertInstanceOf(List.class, cached);
        for (Object element : (List<?>) cached) {
            assertInstanceOf(UserResponse.class, element,
                    () -> "usersList cache hit returned element " + element.getClass().getName());
        }
        assertEquals(original, cached);
    }
}
