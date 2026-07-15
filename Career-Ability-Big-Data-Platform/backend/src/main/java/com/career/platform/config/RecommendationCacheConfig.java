package com.career.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.career.platform.recommend.dto.RecommendationCacheEntry;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/** Redis-specific cache settings. The simple/no-cache test profiles keep Spring Boot defaults. */
@Configuration
@EnableCaching
public class RecommendationCacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer recommendationCacheCustomizer(ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<RecommendationCacheEntry> serializer =
                new Jackson2JsonRedisSerializer<>(RecommendationCacheEntry.class);
        serializer.setObjectMapper(objectMapper.copy());
        RedisCacheConfiguration recommendationConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return builder -> builder.withCacheConfiguration("recommend", recommendationConfig);
    }
}
