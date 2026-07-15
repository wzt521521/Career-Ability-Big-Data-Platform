package com.career.platform.report.repository;

import com.career.platform.report.entity.ReportRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ReportRecordRepository extends JpaRepository<ReportRecord, Long> {

    Page<ReportRecord> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    Page<ReportRecord> findByUserIdAndStatusOrderByCreateTimeDesc(Long userId, String status, Pageable pageable);

    Page<ReportRecord> findByUserIdAndReportTitleContainingOrderByCreateTimeDesc(
            Long userId, String keyword, Pageable pageable);

    Page<ReportRecord> findByUserIdAndStatusAndReportTitleContainingOrderByCreateTimeDesc(
            Long userId, String status, String keyword, Pageable pageable);

    List<ReportRecord> findByStatus(String status);

    List<ReportRecord> findByStatusAndUpdateTimeBefore(String status, LocalDateTime threshold);

    /** Atomically claims a queued record so duplicate AFTER_COMMIT/recovery events cannot render twice. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReportRecord record set record.status = 'GENERATING', "
            + "record.generationStartedAt = :startedAt, "
            + "record.generationAttempts = coalesce(record.generationAttempts, 0) + 1, "
            + "record.errorMsg = null where record.id = :id and record.status = 'PENDING'")
    int claimPendingForGeneration(@Param("id") Long id, @Param("startedAt") LocalDateTime startedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReportRecord record set record.timeRangeStart = :startDate, record.timeRangeEnd = :endDate, "
            + "record.analysisScope = :analysisScope where record.id = :id and record.status = 'GENERATING' "
            + "and record.generationAttempts = :attempt")
    int updateSnapshot(@Param("id") Long id, @Param("attempt") int attempt,
                       @Param("startDate") java.time.LocalDate startDate,
                       @Param("endDate") java.time.LocalDate endDate,
                       @Param("analysisScope") String analysisScope);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReportRecord record set record.status = 'COMPLETED', record.filePath = :filePath, "
            + "record.fileSize = :fileSize, record.errorMsg = null where record.id = :id "
            + "and record.status = 'GENERATING' and record.generationAttempts = :attempt")
    int completeClaimedGeneration(@Param("id") Long id, @Param("attempt") int attempt,
                                  @Param("filePath") String filePath, @Param("fileSize") long fileSize);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReportRecord record set record.status = 'FAILED', record.errorMsg = :errorMsg "
            + "where record.id = :id and record.status = 'GENERATING' and record.generationAttempts = :attempt")
    int failClaimedGeneration(@Param("id") Long id, @Param("attempt") int attempt,
                              @Param("errorMsg") String errorMsg);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReportRecord record set record.status = 'PENDING', record.generationStartedAt = null, "
            + "record.errorMsg = :message where record.id = :id and record.status = :expectedStatus "
            + "and coalesce(record.generationAttempts, 0) = :attempt")
    int requeueIfUnchanged(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                           @Param("attempt") int attempt, @Param("message") String message);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReportRecord record set record.status = 'FAILED', record.errorMsg = :message "
            + "where record.id = :id and record.status = :expectedStatus "
            + "and coalesce(record.generationAttempts, 0) = :attempt")
    int failRecoveryIfUnchanged(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                                @Param("attempt") int attempt, @Param("message") String message);

    Optional<ReportRecord> findByIdAndUserId(Long id, Long userId);
}
