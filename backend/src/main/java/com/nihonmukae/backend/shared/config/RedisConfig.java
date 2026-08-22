package com.nihonmukae.backend.shared.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// NẾU project đã có RedisConfig.java (vd cho session/rate-limit ở AUTH-*),
// merge bean cacheManager() này vào file đó thay vì tạo file trùng —
// @Configuration trùng tên class sẽ conflict.
@Configuration
@EnableCaching
public class RedisConfig {

    private static final String CACHE_DICT_SEARCH = "dictSearch";
    private static final String CACHE_DICT_LOOKUP = "dictLookup";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // JSON serializer (không phải JDK binary mặc định) — bắt buộc vì DTO
        // (WordResponse, SearchResultResponse...) không implement Serializable.
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofHours(1));

        Map<String, RedisCacheConfiguration> perCacheConfig = new HashMap<>();
        perCacheConfig.put(CACHE_DICT_SEARCH, defaultConfig.entryTtl(Duration.ofHours(24)));
        perCacheConfig.put(CACHE_DICT_LOOKUP, defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }
}