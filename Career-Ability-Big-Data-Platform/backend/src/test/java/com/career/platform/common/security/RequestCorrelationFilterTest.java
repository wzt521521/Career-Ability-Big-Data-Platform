package com.career.platform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesSafeClientRequestIdAndRemovesRequestMdcAfterCompletion() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/overview");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "trace-20260715");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observedRequestId = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                observedRequestId.set(MDC.get("requestId")));

        assertEquals("trace-20260715", observedRequestId.get());
        assertEquals("trace-20260715", response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void replacesUnsafeClientRequestId() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/overview");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "unsafe id\nwith-newline");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertNotEquals("unsafe id\nwith-newline", response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
    }
}
