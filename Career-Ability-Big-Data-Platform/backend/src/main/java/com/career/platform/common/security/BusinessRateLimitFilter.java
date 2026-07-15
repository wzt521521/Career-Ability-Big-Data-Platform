package com.career.platform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Lightweight per-client limiter for regular business APIs; Open API keys keep their stricter quota. */
public class BusinessRateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int requestsPerMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public BusinessRateLimitFilter(ObjectMapper objectMapper, boolean enabled, int requestsPerMinute) {
        this(objectMapper, enabled, requestsPerMinute, Clock.systemUTC());
    }

    BusinessRateLimitFilter(ObjectMapper objectMapper, boolean enabled, int requestsPerMinute, Clock clock) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return !enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !uri.startsWith("/api/")
                || uri.startsWith("/api/open/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long windowStart = clock.millis() / 60_000L;
        String key = clientKey(request) + ":" + windowStart;
        Window window = windows.computeIfAbsent(key, ignored -> new Window(windowStart));
        int used = window.counter.incrementAndGet();
        cleanup(windowStart);
        response.setHeader("X-Business-RateLimit-Limit", Integer.toString(requestsPerMinute));
        response.setHeader("X-Business-RateLimit-Remaining", Integer.toString(Math.max(0, requestsPerMinute - used)));
        if (used > requestsPerMinute) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "code", 429,
                    "message", "Too many requests",
                    "data", Map.of("limit", requestsPerMinute)
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void cleanup(long currentWindowStart) {
        if (windows.size() < 2048) {
            return;
        }
        windows.entrySet().removeIf(entry -> entry.getValue().windowStart < currentWindowStart - 1);
    }

    private static final class Window {
        private final long windowStart;
        private final AtomicInteger counter = new AtomicInteger();

        private Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
