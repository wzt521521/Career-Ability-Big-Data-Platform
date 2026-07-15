package com.career.platform.openapi.filter;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import com.career.platform.auth.security.CustomUserPrincipal;
import com.career.platform.openapi.entity.ApiKey;
import com.career.platform.openapi.ratelimit.ApiRateLimiter;
import com.career.platform.openapi.ratelimit.RateLimitResult;
import com.career.platform.openapi.security.OpenApiRequestContext;
import com.career.platform.openapi.service.ApiCallLogService;
import com.career.platform.openapi.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.StringJoiner;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String OPEN_API_PREFIX = "/api/open/v1/";

    private final ApiKeyService apiKeyService;
    private final ApiRateLimiter rateLimiter;
    private final ApiCallLogService callLogService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(
            ApiKeyService apiKeyService,
            ApiRateLimiter rateLimiter,
            ApiCallLogService callLogService,
            ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
        this.callLogService = callLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String applicationPath = requestUri;
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            applicationPath = requestUri.substring(contextPath.length());
        }
        return !applicationPath.startsWith(OPEN_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        ApiKey apiKey;
        try {
            apiKey = apiKeyService.authenticate(request.getHeader("X-API-Key"));
        } catch (BusinessException exception) {
            writeError(response, exception.getErrorCode(), exception.getMessage());
            return;
        }

        CustomUserPrincipal principal = authenticatedPrincipal();
        if (principal == null) {
            writeError(response, ErrorCode.UNAUTHORIZED, "Bearer token is required for Open API requests");
            record(apiKey, request, response, 0L);
            return;
        }
        if (apiKey.getUserId() == null || !apiKey.getUserId().equals(principal.getId())) {
            writeError(response, ErrorCode.FORBIDDEN, "API key does not belong to the authenticated user");
            record(apiKey, request, response, 0L);
            return;
        }
        OpenApiRequestContext.attach(request, apiKey);

        RateLimitResult rateLimit = rateLimiter.tryAcquire(apiKey.getId(), apiKey.getRateLimit());
        response.setHeader("X-RateLimit-Limit", Integer.toString(rateLimit.getLimit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(rateLimit.getRemaining()));
        if (!rateLimit.isAllowed()) {
            writeError(response, ErrorCode.TOO_MANY_REQUESTS, "API rate limit exceeded");
            record(apiKey, request, response, 0L);
            return;
        }

        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            record(apiKey, request, response, System.currentTimeMillis() - startedAt);
        }
    }

    private CustomUserPrincipal authenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal)) {
            return null;
        }
        return (CustomUserPrincipal) authentication.getPrincipal();
    }

    private void record(
            ApiKey apiKey,
            HttpServletRequest request,
            HttpServletResponse response,
            long duration) {
        try {
            callLogService.record(
                    apiKey,
                    request.getRequestURI(),
                    request.getMethod(),
                    parameterNames(request),
                    clientIp(request),
                    duration,
                    response.getStatus());
        } catch (RuntimeException exception) {
            log.error("Unable to record API call", exception);
        }
    }

    private String parameterNames(HttpServletRequest request) {
        StringJoiner names = new StringJoiner(",", "[", "]");
        request.getParameterMap().keySet().stream().sorted().forEach(names::add);
        return names.toString();
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void writeError(
            HttpServletResponse response,
            ErrorCode errorCode,
            String message) throws IOException {
        response.setStatus(errorCode.getCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.error(errorCode.getCode(), message));
    }
}
