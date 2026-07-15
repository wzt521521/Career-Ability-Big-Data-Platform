package com.career.platform.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Bounded-cardinality counters for asynchronous work that is not covered by HTTP metrics. */
@Component
public class OperationalMetrics {

    public static final String REPORT_GENERATION_FAILURES = "career.report.generation.failures";
    public static final String COLLECTION_TASK_FAILURES = "career.collection.task.failures";

    private final Counter reportGenerationFailures;
    private final Counter retryExhaustedCollectionTaskFailures;
    private final Counter nonRetryableCollectionTaskFailures;

    public OperationalMetrics(MeterRegistry meterRegistry) {
        this.reportGenerationFailures = Counter.builder(REPORT_GENERATION_FAILURES)
                .description("Number of report generation attempts that failed")
                .register(meterRegistry);
        this.retryExhaustedCollectionTaskFailures = Counter.builder(COLLECTION_TASK_FAILURES)
                .description("Number of collection tasks that reached a terminal failure")
                .tag("reason", "retry_exhausted")
                .register(meterRegistry);
        this.nonRetryableCollectionTaskFailures = Counter.builder(COLLECTION_TASK_FAILURES)
                .description("Number of collection tasks that reached a terminal failure")
                .tag("reason", "non_retryable")
                .register(meterRegistry);
    }

    public void recordReportGenerationFailure() {
        reportGenerationFailures.increment();
    }

    public void recordCollectionTaskFailure(boolean retryable) {
        if (retryable) {
            retryExhaustedCollectionTaskFailures.increment();
            return;
        }
        nonRetryableCollectionTaskFailures.increment();
    }
}
