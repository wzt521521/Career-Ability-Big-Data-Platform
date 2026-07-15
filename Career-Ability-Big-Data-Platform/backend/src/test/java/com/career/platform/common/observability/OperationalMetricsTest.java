package com.career.platform.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OperationalMetricsTest {

    @Test
    void recordsBoundedReportAndCollectionFailureCounters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(meterRegistry);

        metrics.recordReportGenerationFailure();
        metrics.recordCollectionTaskFailure(true);
        metrics.recordCollectionTaskFailure(false);

        assertEquals(1.0, meterRegistry.find(OperationalMetrics.REPORT_GENERATION_FAILURES)
                .counter().count());
        assertEquals(1.0, meterRegistry.find(OperationalMetrics.COLLECTION_TASK_FAILURES)
                .tag("reason", "retry_exhausted").counter().count());
        assertEquals(1.0, meterRegistry.find(OperationalMetrics.COLLECTION_TASK_FAILURES)
                .tag("reason", "non_retryable").counter().count());
    }
}
