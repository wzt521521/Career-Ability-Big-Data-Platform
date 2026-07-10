package com.career.platform.analytics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AnalyticsScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsScheduler.class);
    private final OfflineAnalysisService analysisService;
    private final StringRedisTemplate redis;
    private final AtomicBoolean localLock = new AtomicBoolean(false);
    private final String lockValue = UUID.randomUUID().toString();

    @Value("${app.analytics.lock-key}")
    private String lockKey;

    @Value("${app.analytics.lock-seconds}")
    private long lockSeconds;

    public AnalyticsScheduler(OfflineAnalysisService analysisService, StringRedisTemplate redis) {
        this.analysisService = analysisService;
        this.redis = redis;
    }

    @Scheduled(cron = "${app.analytics.schedule}")
    public void runDaily() {
        if (!localLock.compareAndSet(false, true)) {
            return;
        }
        boolean redisLocked = false;
        try {
            try {
                redisLocked = Boolean.TRUE.equals(redis.opsForValue()
                        .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(lockSeconds)));
                if (!redisLocked) {
                    LOGGER.info("Analytics run skipped because another instance owns the lock");
                    return;
                }
            } catch (RuntimeException redisUnavailable) {
                LOGGER.warn("Redis lock unavailable; using the local single-instance lock");
            }
            analysisService.calculateAndPersist();
        } finally {
            if (redisLocked) {
                try {
                    String currentValue = redis.opsForValue().get(lockKey);
                    if (lockValue.equals(currentValue)) redis.delete(lockKey);
                } catch (RuntimeException ignored) {
                    LOGGER.warn("Unable to release Redis analytics lock; it will expire automatically");
                }
            }
            localLock.set(false);
        }
    }
}
