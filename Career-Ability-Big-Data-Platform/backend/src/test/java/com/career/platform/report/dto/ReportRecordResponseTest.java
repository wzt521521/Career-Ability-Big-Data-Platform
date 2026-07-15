package com.career.platform.report.dto;

import com.career.platform.report.entity.ReportRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReportRecordResponseTest {

    @Test
    void neverReturnsStoredInternalFailureDetails() {
        ReportRecord record = new ReportRecord();
        record.setStatus("FAILED");
        record.setErrorMsg("Unable to load /var/lib/career-ability/reports/private.ftl; token=top-secret");

        ReportRecordResponse response = new ReportRecordResponse(record);

        assertEquals("Report generation failed. Please retry.", response.getErrorMsg());
        assertFalse(response.getErrorMsg().contains("/var/lib"));
        assertFalse(response.getErrorMsg().contains("top-secret"));
    }

    @Test
    void hidesRecoveryDetailsUntilTheRecordIsActuallyFailed() {
        ReportRecord record = new ReportRecord();
        record.setStatus("PENDING");
        record.setErrorMsg("Detected stale task at /var/lib/career-ability/reports");

        assertNull(new ReportRecordResponse(record).getErrorMsg());
    }
}
