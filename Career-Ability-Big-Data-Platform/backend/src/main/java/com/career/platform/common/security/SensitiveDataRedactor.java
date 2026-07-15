package com.career.platform.common.security;

import java.util.regex.Pattern;

public final class SensitiveDataRedactor {

    private static final String SENSITIVE_NAME = "(?:password|token|api[_-]?key|authorization|secret)";
    private static final Pattern JSON_FIELD = Pattern.compile(
            "(?i)(\\\"" + SENSITIVE_NAME + "\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(" + SENSITIVE_NAME + "\\s*[=:]\\s*)([^,\\s;&]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[^\\s,;]+");
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/][^\\s,;]+|(?<!:)/(?:[^\\s,;/]+/)+[^\\s,;]+)");

    private SensitiveDataRedactor() {
    }

    public static String redact(String value) {
        if (value == null) {
            return null;
        }
        String redacted = JSON_FIELD.matcher(value).replaceAll("$1[REDACTED]$2");
        redacted = KEY_VALUE.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = BEARER.matcher(redacted).replaceAll("$1[REDACTED]");
        return ABSOLUTE_PATH.matcher(redacted).replaceAll("[REDACTED_PATH]");
    }
}
