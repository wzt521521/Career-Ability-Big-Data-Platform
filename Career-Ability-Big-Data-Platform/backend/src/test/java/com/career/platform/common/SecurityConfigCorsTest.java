package com.career.platform.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.career.platform.common.security.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class SecurityConfigCorsTest {

    @Test
    void rejectsWildcardOriginsAndDisablesCookieCredentials() {
        SecurityConfig config = new SecurityConfig();

        assertThrows(IllegalStateException.class, () -> config.corsConfigurationSource("*"));

        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) config
                .corsConfigurationSource("https://console.example.test");
        CorsConfiguration cors = source.getCorsConfigurations().get("/**");
        assertFalse(Boolean.TRUE.equals(cors.getAllowCredentials()));
        assertTrue(cors.getExposedHeaders().contains("Content-Disposition"));
        assertTrue(cors.getAllowedHeaders().contains(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertTrue(cors.getExposedHeaders().contains(RequestCorrelationFilter.REQUEST_ID_HEADER));
    }
}
