package com.career.platform.openapi.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryApiRateLimiterTest {

    @Test
    void concurrentlyAllowsOnlyTheConfiguredLimit() {
        InMemoryApiRateLimiter limiter = new InMemoryApiRateLimiter();

        long allowed = IntStream.range(0, 200)
                .parallel()
                .filter(ignored -> limiter.tryAcquire(42L, 10).isAllowed())
                .count();

        assertEquals(10L, allowed);
    }
}
