package com.career.platform.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.career.platform.report.service.ReportStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Status;

class ReportStorageHealthIndicatorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void reportsUpForWritableReportDirectory() {
        ReportStorageHealthIndicator indicator = new ReportStorageHealthIndicator(
                new ReportStorage(tempDirectory.resolve("reports").toString()));

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void reportsDownWhenConfiguredReportDirectoryIsAFile() throws Exception {
        Path file = Files.createFile(tempDirectory.resolve("report-file"));
        ReportStorageHealthIndicator indicator = new ReportStorageHealthIndicator(
                new ReportStorage(file.toString()));

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }
}
