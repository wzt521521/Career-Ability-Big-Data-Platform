package com.career.platform.common.observability;

import com.career.platform.report.service.ReportStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Verifies that the filesystem used by asynchronous report rendering is writable. */
@Component
public class ReportStorageHealthIndicator implements HealthIndicator {

    private final ReportStorage reportStorage;

    public ReportStorageHealthIndicator(ReportStorage reportStorage) {
        this.reportStorage = reportStorage;
    }

    @Override
    public Health health() {
        Path root = reportStorage.getRoot();
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root) || !Files.isWritable(root)) {
                return Health.down().withDetail("storage", "not_writable").build();
            }
            return Health.up().withDetail("storage", "writable").build();
        } catch (IOException | SecurityException exception) {
            return Health.down().withDetail("storage", "unavailable").build();
        }
    }
}
