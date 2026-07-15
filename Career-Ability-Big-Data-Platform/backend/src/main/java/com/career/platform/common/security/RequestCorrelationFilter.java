package com.career.platform.common.security;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a bounded request identifier to logs and echoes it to API clients. */
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        long startedAt = System.nanoTime();
        try {
            MDC.put("requestId", requestId);
            MDC.put("httpMethod", request.getMethod());
            MDC.put("requestPath", request.getRequestURI());
            response.setHeader(REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.put("httpStatus", Integer.toString(response.getStatus()));
            MDC.put("durationMs", Long.toString((System.nanoTime() - startedAt) / 1_000_000));
            log.info("HTTP request completed");
            restoreContext(previousContext);
        }
    }

    private String requestId(String suppliedRequestId) {
        if (suppliedRequestId != null && SAFE_REQUEST_ID.matcher(suppliedRequestId).matches()) {
            return suppliedRequestId;
        }
        return UUID.randomUUID().toString();
    }

    private void restoreContext(Map<String, String> previousContext) {
        if (previousContext == null || previousContext.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(previousContext);
    }
}
