package com.career.platform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BusinessRateLimitFilterTest {

    @Test
    void rejectsRequestsAboveLimitAndExposesHeaders() throws Exception {
        BusinessRateLimitFilter filter = new BusinessRateLimitFilter(
                new ObjectMapper(),
                true,
                2,
                Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC));
        AtomicInteger chainCalls = new AtomicInteger();

        MockHttpServletResponse first = execute(filter, chainCalls);
        MockHttpServletResponse second = execute(filter, chainCalls);
        MockHttpServletResponse third = execute(filter, chainCalls);

        assertEquals(200, first.getStatus());
        assertEquals("1", first.getHeader("X-Business-RateLimit-Remaining"));
        assertEquals(200, second.getStatus());
        assertEquals(429, third.getStatus());
        assertEquals(2, chainCalls.get());
    }

    @Test
    void skipsOpenApiBecauseItHasApiKeySpecificLimits() throws Exception {
        BusinessRateLimitFilter filter = new BusinessRateLimitFilter(
                new ObjectMapper(),
                true,
                1,
                Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/open/v1/positions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainCalls.incrementAndGet());

        assertEquals(200, response.getStatus());
        assertEquals(1, chainCalls.get());
    }

    private MockHttpServletResponse execute(BusinessRateLimitFilter filter, AtomicInteger chainCalls) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/positions");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainCalls.incrementAndGet());
        return response;
    }
}
