package com.career.platform.report.service;

import com.career.platform.analytics.service.AnalyticsService;
import com.career.platform.common.observability.OperationalMetrics;
import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.repository.ReportTemplateRepository;
import freemarker.template.Configuration;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncReportGeneratorTest {

    @Test
    void persistsOnlyThePublicFailureMessageWhenAnInternalExceptionContainsSensitiveDetails() {
        ReportTemplateRepository templateRepository = mock(ReportTemplateRepository.class);
        ReportGenerationStateService stateService = mock(ReportGenerationStateService.class);
        OperationalMetrics operationalMetrics = mock(OperationalMetrics.class);
        ReportRecord record = new ReportRecord();
        record.setId(71L);
        record.setTemplateId(9L);
        record.setUserId(17L);

        when(stateService.claim(71L)).thenReturn(record);
        when(templateRepository.findById(9L)).thenThrow(new IllegalStateException(
                "Unable to load /var/lib/career-ability/reports/private.ftl; token=top-secret"));

        AsyncReportGenerator generator = new AsyncReportGenerator(
                templateRepository,
                mock(AnalyticsService.class),
                mock(Configuration.class),
                mock(ReportSnapshotMapper.class),
                mock(PdfReportRenderer.class),
                mock(ReportStorage.class),
                stateService,
                operationalMetrics);

        generator.generate(71L);

        verify(stateService).fail(eq(record), eq("Report generation failed. Please retry."));
        verify(operationalMetrics).recordReportGenerationFailure();
    }
}
