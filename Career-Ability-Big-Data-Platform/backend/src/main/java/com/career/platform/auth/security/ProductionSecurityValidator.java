package com.career.platform.auth.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Keeps accidental development JWT configuration from reaching a production profile.
 */
@Component
public class ProductionSecurityValidator {

    private static final String DEVELOPMENT_SECRET = "local-development-secret-change-me-32-bytes";

    private final Environment environment;
    private final JwtProperties jwtProperties;

    public ProductionSecurityValidator(Environment environment, JwtProperties jwtProperties) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void validate() {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(profile -> "prod".equals(profile) || "production".equals(profile));
        if (!production) {
            return;
        }
        String secret = jwtProperties.getSecret();
        if (secret == null
                || secret.isBlank()
                || DEVELOPMENT_SECRET.equals(secret)
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "A non-default SECURITY_JWT_SECRET with at least 32 bytes is required in production");
        }
    }
}
