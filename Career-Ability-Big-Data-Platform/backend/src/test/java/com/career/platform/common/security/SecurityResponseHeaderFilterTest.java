package com.career.platform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityResponseHeaderFilterTest {

    @Test
    void appliesApiHeadersAndHstsOnlyToSecureRequests() throws Exception {
        SecurityResponseHeaderFilter filter = new SecurityResponseHeaderFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/overview");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals("max-age=31536000; includeSubDomains", response.getHeader("Strict-Transport-Security"));
        assertEquals("default-src 'none'; base-uri 'none'; frame-ancestors 'self'",
                response.getHeader("Content-Security-Policy"));
    }

    @Test
    void doesNotSetApiCspOrHstsForInsecureSwaggerRequests() throws Exception {
        SecurityResponseHeaderFilter filter = new SecurityResponseHeaderFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertNull(response.getHeader("Content-Security-Policy"));
        assertNull(response.getHeader("Strict-Transport-Security"));
    }
}
