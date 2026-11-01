package io.aetheris.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.users.dto.UserResponse;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {
    // Old usersList:: / userById:: entries may use a polymorphic serializer
    // incompatible with immutable JDK lists. Never read those entries under
    // the new strict, typed JSON contract. They expire by the old TTL.
    private static final String CACHE_SCHEMA_PREFIX = "aetheris:users:v2:";

    @Bean
    RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .disableCachingNullValues()
                .computePrefixWith(name -> CACHE_SCHEMA_PREFIX + name + "::");
    }

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory factory,
                                   RedisCacheConfiguration defaults) {
        ObjectMapper mapper = new ObjectMapper();

        // The two production cache value types are fixed and whitelisted.
        // Do not enable arbitrary JSON @class polymorphic deserialization.
        var userListType = mapper.getTypeFactory()
                .constructCollectionType(ArrayList.class, UserResponse.class);
        Jackson2JsonRedisSerializer<List<UserResponse>> listSerializer =
                new Jackson2JsonRedisSerializer<>(mapper, userListType);
        Jackson2JsonRedisSerializer<UserResponse> userSerializer =
                new Jackson2JsonRedisSerializer<>(mapper, UserResponse.class);

        RedisCacheConfiguration lists = defaults.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(listSerializer));
        RedisCacheConfiguration single = defaults.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(userSerializer));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        "usersList", lists,
                        "userById", single))
                .disableCreateOnMissingCache()
                .build();
    }
}
