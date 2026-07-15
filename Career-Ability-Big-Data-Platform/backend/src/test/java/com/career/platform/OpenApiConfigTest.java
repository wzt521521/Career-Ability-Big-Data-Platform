package com.career.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.career.platform.config.OpenApiConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void publishesBearerAndApiKeySecuritySchemes() {
        OpenAPI openAPI = new OpenApiConfig().platformOpenApi();

        SecurityScheme bearer = openAPI.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH);
        SecurityScheme apiKey = openAPI.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.API_KEY_AUTH);
        assertNotNull(bearer);
        assertEquals("bearer", bearer.getScheme());
        assertNotNull(apiKey);
        assertEquals("X-API-Key", apiKey.getName());
    }

    @Test
    void documentsOpenEndpointsAsRequiringBothCredentials() {
        OpenApiConfig config = new OpenApiConfig();
        Operation operation = new Operation();
        OpenAPI openAPI = config.platformOpenApi().paths(new Paths()
                .addPathItem("/api/open/v1/platform/status", new PathItem().get(operation)));

        config.openApiSecurityCustomiser().customise(openAPI);

        assertEquals(1, operation.getSecurity().size());
        assertNotNull(operation.getSecurity().get(0).get(OpenApiConfig.BEARER_AUTH));
        assertNotNull(operation.getSecurity().get(0).get(OpenApiConfig.API_KEY_AUTH));
        assertNotNull(operation.getResponses().get("400"));
        assertNotNull(operation.getResponses().get("429"));
        assertNotNull(operation.getResponses().get("429").getHeaders().get("X-RateLimit-Limit"));
    }
}
