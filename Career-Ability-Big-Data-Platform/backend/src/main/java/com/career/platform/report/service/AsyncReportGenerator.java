package com.career.platform.report.service;

import com.career.platform.analytics.dto.AnalyticsSnapshotResponse;
import com.career.platform.analytics.service.AnalyticsService;
import com.career.platform.common.observability.OperationalMetrics;
import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.repository.ReportTemplateRepository;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 独立的异步报告生成服务。
 * <p>
 * 必须独立于 {@code ReportServiceImpl}，否则 {@code @Async} 因 Spring AOP 自调用限制
 * 不会生效——PDF 生成会阻塞 Tomcat 请求线程。
 * </p>
 */
@Service
public class AsyncReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(AsyncReportGenerator.class);
    private static final String PUBLIC_FAILURE_MESSAGE = "Report generation failed. Please retry.";

    private final ReportTemplateRepository templateRepository;
    private final AnalyticsService analyticsService;
    private final Configuration freemarkerConfig;
    private final ReportSnapshotMapper snapshotMapper;
    private final PdfReportRenderer pdfReportRenderer;
    private final ReportStorage reportStorage;
    private final ReportGenerationStateService stateService;
    private final OperationalMetrics operationalMetrics;

    public AsyncReportGenerator(ReportTemplateRepository templateRepository,
                                AnalyticsService analyticsService,
                                Configuration freemarkerConfig,
                                ReportSnapshotMapper snapshotMapper,
                                PdfReportRenderer pdfReportRenderer,
                                ReportStorage reportStorage,
                                ReportGenerationStateService stateService,
                                OperationalMetrics operationalMetrics) {
        this.templateRepository = templateRepository;
        this.analyticsService = analyticsService;
        this.freemarkerConfig = freemarkerConfig;
        this.snapshotMapper = snapshotMapper;
        this.pdfReportRenderer = pdfReportRenderer;
        this.reportStorage = reportStorage;
        this.stateService = stateService;
        this.operationalMetrics = operationalMetrics;
    }

    /**
     * 异步生成 PDF 报告。
     * <p>
     * 流程：获取统计数据 → Freemarker 渲染 HTML → iText 转换 PDF
     * → 写入文件系统 → 更新数据库状态。
     * </p>
     *
     * @param recordId       报告记录 ID
     */
    @Async("reportExecutor")
    public void generate(Long recordId) {
        ReportRecord record = stateService.claim(recordId);
        if (record == null) {
            log.info("跳过未成功认领的报告任务: recordId={}", recordId);
            return;
        }
        Path pdfPath = null;
        try {
            String templateFile = templateRepository.findById(record.getTemplateId())
                    .filter(template -> Integer.valueOf(1).equals(template.getStatus()))
                    .map(template -> template.getTemplateFile())
                    .orElseThrow(() -> new IllegalStateException("报告模板不存在或已禁用"));

            // 1. 使用 C 模块的可复现快照契约，日期和过滤条件均真实参与查询。
            AnalyticsSnapshotResponse snapshot = analyticsService.snapshot(snapshotMapper.toSnapshotRequest(record));
            String analysisScope = snapshotMapper.serializeScope(snapshot, record);
            if (!stateService.saveSnapshot(record, snapshot.getStartDate(), snapshot.getEndDate(), analysisScope)) {
                log.warn("报告任务在快照阶段已被恢复任务接管: recordId={}", recordId);
                return;
            }
            record.setTimeRangeStart(snapshot.getStartDate());
            record.setTimeRangeEnd(snapshot.getEndDate());
            record.setAnalysisScope(analysisScope);
            Map<String, Object> dataModel = snapshotMapper.toTemplateModel(snapshot, record);

            // 2. Freemarker 渲染 HTML
            Template template = freemarkerConfig.getTemplate(templateFile);
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            String html = writer.toString();

            // 3. HTML -> PDF. ReportStorage owns a single configured root and validates every path.
            pdfPath = reportStorage.allocatePdf(record.getUserId());
            Files.write(pdfPath, pdfReportRenderer.render(html));

            // 4. 更新记录
            long fileSize = Files.size(pdfPath);
            if (!stateService.complete(record, pdfPath, fileSize)) {
                Files.deleteIfExists(pdfPath);
                log.warn("报告任务在完成阶段已被其他尝试接管: recordId={}", recordId);
                return;
            }

            log.info("报告生成完成: recordId={}, title={}, size={}", recordId, record.getReportTitle(), fileSize);

        } catch (Exception e) {
            operationalMetrics.recordReportGenerationFailure();
            if (pdfPath != null) {
                try {
                    Files.deleteIfExists(pdfPath);
                } catch (Exception cleanupException) {
                    log.warn("清理失败的报告文件失败: {}", pdfPath, cleanupException);
                }
            }
            log.error("报告生成失败: recordId={}", recordId, e);
            if (record != null) {
                stateService.fail(record, PUBLIC_FAILURE_MESSAGE);
            }
        }
    }
}
