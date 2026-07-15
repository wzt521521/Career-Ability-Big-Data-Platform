package com.career.platform.report.service;

import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.repository.ReportRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Requeues records left behind by a process restart or an abandoned asynchronous worker. */
@Component
public class ReportRecoveryService {

    private final ReportRecordRepository recordRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxAttempts;
    private final long pendingTimeoutSeconds;
    private final long generatingTimeoutSeconds;

    public ReportRecoveryService(ReportRecordRepository recordRepository,
                                 ApplicationEventPublisher eventPublisher,
                                 @Value("${app.report.max-attempts:3}") int maxAttempts,
                                 @Value("${app.report.pending-timeout-seconds:120}") long pendingTimeoutSeconds,
                                 @Value("${app.report.generating-timeout-seconds:1800}") long generatingTimeoutSeconds) {
        this.recordRepository = recordRepository;
        this.eventPublisher = eventPublisher;
        this.maxAttempts = maxAttempts;
        this.pendingTimeoutSeconds = pendingTimeoutSeconds;
        this.generatingTimeoutSeconds = generatingTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${app.report.recovery-delay-ms:60000}", initialDelayString = "${app.report.recovery-delay-ms:60000}")
    @Transactional
    public void recoverStaleReports() {
        LocalDateTime now = LocalDateTime.now();
        recover(recordRepository.findByStatusAndUpdateTimeBefore("PENDING", now.minusSeconds(pendingTimeoutSeconds)), now);
        recover(recordRepository.findByStatusAndUpdateTimeBefore("GENERATING", now.minusSeconds(generatingTimeoutSeconds)), now);
    }

    private void recover(List<ReportRecord> records, LocalDateTime now) {
        for (ReportRecord record : records) {
            int attempts = record.getGenerationAttempts() == null ? 0 : record.getGenerationAttempts();
            if (attempts >= maxAttempts) {
                recordRepository.failRecoveryIfUnchanged(record.getId(), record.getStatus(), attempts,
                        "报告生成超过最大重试次数，需重新提交");
                continue;
            }
            if (recordRepository.requeueIfUnchanged(record.getId(), record.getStatus(), attempts,
                    "检测到未完成任务，已于 " + now + " 重新排队") == 1) {
                eventPublisher.publishEvent(new ReportGenerationRequestedEvent(record.getId()));
            }
        }
    }
}
