package com.career.platform.report.service;

import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.repository.ReportRecordRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportGenerationStateServiceTest {

    private ReportRecordRepository repository;
    private ReportGenerationStateService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReportRecordRepository.class);
        service = new ReportGenerationStateService(repository);
    }

    @Test
    void claimsOnlyTheWorkerWhoseConditionalUpdateSucceeds() {
        ReportRecord record = record(7L, 1);
        when(repository.claimPendingForGeneration(eq(7L), any(LocalDateTime.class))).thenReturn(1, 0);
        when(repository.findById(7L)).thenReturn(Optional.of(record));

        assertSame(record, service.claim(7L));
        assertNull(service.claim(7L));

        verify(repository).findById(7L);
    }

    @Test
    void doesNotLetAnOlderAttemptMarkTheRecoveredAttemptCompleted() {
        ReportRecord record = record(7L, 1);
        when(repository.completeClaimedGeneration(eq(7L), eq(1), any(), eq(64L))).thenReturn(0);

        assertFalse(service.complete(record, java.nio.file.Path.of("report.pdf"), 64));
        verify(repository).completeClaimedGeneration(eq(7L), eq(1), any(), eq(64L));
        verify(repository, never()).save(record);
    }

    private ReportRecord record(Long id, int attempts) {
        ReportRecord record = new ReportRecord();
        record.setId(id);
        record.setGenerationAttempts(attempts);
        return record;
    }
}
