package com.career.platform.openapi.security;

import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import com.career.platform.openapi.entity.ApiKey;
import javax.servlet.http.HttpServletRequest;

/**
 * Exposes the already authenticated API key caller without exposing key material.
 */
public final class OpenApiRequestContext {

    public static final String CALLER_ATTRIBUTE = OpenApiRequestContext.class.getName() + ".caller";

    private OpenApiRequestContext() {
    }

    public static void attach(HttpServletRequest request, ApiKey apiKey) {
        request.setAttribute(
                CALLER_ATTRIBUTE,
                new OpenApiCaller(apiKey.getId(), apiKey.getUserId(), apiKey.getAppName()));
    }

    public static OpenApiCaller requireCaller(HttpServletRequest request) {
        Object caller = request.getAttribute(CALLER_ATTRIBUTE);
        if (caller instanceof OpenApiCaller) {
            return (OpenApiCaller) caller;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Open API caller is not authenticated");
    }
}
