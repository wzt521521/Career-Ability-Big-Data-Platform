package com.career.platform.report.service;

/** Published inside the report-creation transaction and consumed only after it commits. */
public final class ReportGenerationRequestedEvent {

    private final Long reportId;

    public ReportGenerationRequestedEvent(Long reportId) {
        this.reportId = reportId;
    }

    public Long getReportId() {
        return reportId;
    }
}
