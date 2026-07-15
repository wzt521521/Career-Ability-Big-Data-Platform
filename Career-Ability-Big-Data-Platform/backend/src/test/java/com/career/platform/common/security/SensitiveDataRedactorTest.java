package com.career.platform.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveDataRedactorTest {

    @Test
    void removesCredentialsAndAbsolutePathsFromMessages() {
        String raw = "password=secret-value token=eyJhbGciOiJIUzI1NiJ9 "
                + "Bearer another-token X-API-Key: api-secret C:\\reports\\user.pdf";

        String redacted = SensitiveDataRedactor.redact(raw);

        assertFalse(redacted.contains("secret-value"));
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9"));
        assertFalse(redacted.contains("another-token"));
        assertFalse(redacted.contains("api-secret"));
        assertFalse(redacted.contains("C:\\reports"));
        assertTrue(redacted.contains("[REDACTED]"));
    }
}
