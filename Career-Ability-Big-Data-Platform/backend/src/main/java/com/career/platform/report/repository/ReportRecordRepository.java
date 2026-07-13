package com.career.platform.report.repository;

import com.career.platform.report.entity.ReportRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRecordRepository extends JpaRepository<ReportRecord, Long> {

    Page<ReportRecord> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    Page<ReportRecord> findByUserIdAndStatusOrderByCreateTimeDesc(Long userId, String status, Pageable pageable);

    Page<ReportRecord> findByUserIdAndReportTitleContainingOrderByCreateTimeDesc(
            Long userId, String keyword, Pageable pageable);

    Page<ReportRecord> findByUserIdAndStatusAndReportTitleContainingOrderByCreateTimeDesc(
            Long userId, String status, String keyword, Pageable pageable);

    List<ReportRecord> findByStatus(String status);

    Optional<ReportRecord> findByIdAndUserId(Long id, Long userId);
}
