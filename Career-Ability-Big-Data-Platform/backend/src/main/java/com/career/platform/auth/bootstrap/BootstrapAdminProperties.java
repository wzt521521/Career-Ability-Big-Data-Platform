package com.career.platform.auth.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap.admin")
public class BootstrapAdminProperties {

    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isConfigured() {
        return hasText(username) && hasText(password);
    }

    public boolean isPartiallyConfigured() {
        return hasText(username) != hasText(password);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
