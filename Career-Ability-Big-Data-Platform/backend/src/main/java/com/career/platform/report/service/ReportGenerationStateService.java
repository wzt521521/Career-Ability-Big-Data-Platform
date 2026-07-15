package com.career.platform.report.service;

import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.repository.ReportRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persists each report-worker state transition in a separate short transaction. This makes
 * GENERATING visible to polling clients before expensive rendering starts and prevents a stale
 * worker from writing over a recovered attempt.
 */
@Service
public class ReportGenerationStateService {

    private final ReportRecordRepository recordRepository;

    public ReportGenerationStateService(ReportRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReportRecord claim(Long recordId) {
        if (recordRepository.claimPendingForGeneration(recordId, LocalDateTime.now()) != 1) {
            return null;
        }
        return recordRepository.findById(recordId).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveSnapshot(ReportRecord record, LocalDate startDate, LocalDate endDate, String scope) {
        return recordRepository.updateSnapshot(record.getId(), attempt(record), startDate, endDate, scope) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(ReportRecord record, Path pdfPath, long fileSize) {
        return recordRepository.completeClaimedGeneration(
                record.getId(), attempt(record), pdfPath.toString(), fileSize) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(ReportRecord record, String errorMessage) {
        recordRepository.failClaimedGeneration(record.getId(), attempt(record), errorMessage);
    }

    private int attempt(ReportRecord record) {
        return record.getGenerationAttempts() == null ? 0 : record.getGenerationAttempts();
    }
}
