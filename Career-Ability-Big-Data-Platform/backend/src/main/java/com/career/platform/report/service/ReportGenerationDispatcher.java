package com.career.platform.report.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts background work only after the report record is durably committed. */
@Component
public class ReportGenerationDispatcher {

    private final AsyncReportGenerator asyncReportGenerator;

    public ReportGenerationDispatcher(AsyncReportGenerator asyncReportGenerator) {
        this.asyncReportGenerator = asyncReportGenerator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ReportGenerationRequestedEvent event) {
        asyncReportGenerator.generate(event.getReportId());
    }
}
