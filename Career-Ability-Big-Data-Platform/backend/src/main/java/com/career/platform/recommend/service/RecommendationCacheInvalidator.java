package com.career.platform.recommend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Keeps recommendation cache invalidation in one place so profile and ingestion flows can
 * invalidate results without depending on the recommendation implementation.
 */
@Component
public class RecommendationCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(RecommendationCacheInvalidator.class);
    private static final String CACHE_NAME = "recommend";

    private final CacheManager cacheManager;

    public RecommendationCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        // Position-data versions are part of cache keys, so enumerating all older user keys
        // would require Redis key scans. The cache contains only bounded TOP20 entries; clear is
        // deterministic and ensures a profile change never reads a stale versioned value.
        withCache(Cache::clear);
    }

    public void evictAll() {
        withCache(Cache::clear);
    }

    private void withCache(java.util.function.Consumer<Cache> action) {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                action.accept(cache);
            }
        } catch (RuntimeException exception) {
            // Recommendation reads must keep working when Redis is unavailable.
            log.warn("推荐缓存失效操作失败，后续请求将直接计算推荐结果", exception);
        }
    }
}
