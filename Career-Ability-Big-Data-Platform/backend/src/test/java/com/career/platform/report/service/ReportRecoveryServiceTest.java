package com.career.platform.report.service;

import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.repository.ReportRecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportRecoveryServiceTest {

    private ReportRecordRepository repository;
    private ApplicationEventPublisher eventPublisher;
    private ReportRecoveryService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReportRecordRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ReportRecoveryService(repository, eventPublisher, 3, 60, 300);
    }

    @Test
    void requeuesOnlyAnUnchangedStaleWorkerAndPublishesAfterTheTransaction() {
        ReportRecord stale = record(8L, "GENERATING", 1);
        when(repository.findByStatusAndUpdateTimeBefore(eq("PENDING"), any(LocalDateTime.class))).thenReturn(List.of());
        when(repository.findByStatusAndUpdateTimeBefore(eq("GENERATING"), any(LocalDateTime.class))).thenReturn(List.of(stale));
        when(repository.requeueIfUnchanged(eq(8L), eq("GENERATING"), eq(1), any())).thenReturn(1);

        service.recoverStaleReports();

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(8L, ((ReportGenerationRequestedEvent) event.getValue()).getReportId());
    }

    @Test
    void doesNotPublishWhenAnotherWorkerChangedTheStaleRecordFirst() {
        ReportRecord stale = record(8L, "GENERATING", 1);
        when(repository.findByStatusAndUpdateTimeBefore(eq("PENDING"), any(LocalDateTime.class))).thenReturn(List.of());
        when(repository.findByStatusAndUpdateTimeBefore(eq("GENERATING"), any(LocalDateTime.class))).thenReturn(List.of(stale));
        when(repository.requeueIfUnchanged(eq(8L), eq("GENERATING"), eq(1), any())).thenReturn(0);

        service.recoverStaleReports();

        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
    }

    private ReportRecord record(Long id, String status, int attempts) {
        ReportRecord record = new ReportRecord();
        record.setId(id);
        record.setStatus(status);
        record.setGenerationAttempts(attempts);
        return record;
    }
}
