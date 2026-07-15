package com.career.platform.common.security;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies browser-facing headers that are safe for JSON API responses. */
public class SecurityResponseHeaderFilter extends OncePerRequestFilter {

    private static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; base-uri 'none'; frame-ancestors 'self'";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("X-XSS-Protection", "0");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), geolocation=(), microphone=()");
        if (isApiOrActuatorRequest(request)) {
            response.setHeader("Content-Security-Policy", API_CONTENT_SECURITY_POLICY);
        }
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        filterChain.doFilter(request, response);
    }

    private boolean isApiOrActuatorRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri.startsWith("/api/") || uri.startsWith("/actuator/");
    }
}
