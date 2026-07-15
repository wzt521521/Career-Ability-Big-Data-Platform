package com.career.platform.auth.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityValidatorTest {

    @Test
    void rejectsDevelopmentSecretInProduction() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("local-development-secret-change-me-32-bytes");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(environment, properties);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void acceptsStrongNonDefaultSecretInProduction() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("production-secret-with-at-least-thirty-two-bytes");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(environment, properties);

        assertDoesNotThrow(validator::validate);
    }
}
