package com.career.platform.openapi.security;

/**
 * Non-secret identity of the API key authenticated for the current request.
 */
public final class OpenApiCaller {

    private final Long apiKeyId;
    private final Long userId;
    private final String appName;

    public OpenApiCaller(Long apiKeyId, Long userId, String appName) {
        this.apiKeyId = apiKeyId;
        this.userId = userId;
        this.appName = appName;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAppName() {
        return appName;
    }
}
