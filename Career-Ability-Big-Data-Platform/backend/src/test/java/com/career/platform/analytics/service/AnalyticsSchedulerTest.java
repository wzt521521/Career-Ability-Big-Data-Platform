package com.career.platform.analytics.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnalyticsSchedulerTest {

    @Mock private OfflineAnalysisService analysisService;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    private AnalyticsScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AnalyticsScheduler(analysisService, redis);
        ReflectionTestUtils.setField(scheduler, "lockKey", "lock:test:analytics");
        ReflectionTestUtils.setField(scheduler, "lockSeconds", 90L);
    }

    @Test
    void acquiresAnExpiringRedisLockRunsAndReleasesOnlyItsOwnLock() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("lock:test:analytics"), any(String.class), eq(Duration.ofSeconds(90))))
                .thenReturn(true);
        scheduler.runDaily();

        verify(analysisService).calculateAndPersist();
        verify(redis).execute(any(), eq(List.of("lock:test:analytics")), any(String.class));
    }

    @Test
    void skipsWhenAnotherInstanceOwnsTheRedisLock() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("lock:test:analytics"), any(String.class), any(Duration.class)))
                .thenReturn(false);

        scheduler.runDaily();

        verify(analysisService, never()).calculateAndPersist();
    }

    @Test
    void fallsBackToTheLocalSingleInstanceLockWhenRedisIsUnavailable() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("offline"));

        scheduler.runDaily();

        verify(analysisService).calculateAndPersist();
        verify(redis, never()).execute(any(), any(), any());
    }
}
