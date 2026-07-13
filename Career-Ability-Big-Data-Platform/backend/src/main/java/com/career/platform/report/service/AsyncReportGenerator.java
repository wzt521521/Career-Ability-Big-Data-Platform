package com.career.platform.report.service;

import com.career.platform.analytics.service.AnalyticsService;
import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.repository.ReportRecordRepository;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

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

    private final ReportRecordRepository recordRepository;
    private final AnalyticsService analyticsService;
    private final Configuration freemarkerConfig;

    @Value("${app.report.output-dir:./upload/reports}")
    private String outputDir;

    public AsyncReportGenerator(ReportRecordRepository recordRepository,
                                AnalyticsService analyticsService,
                                Configuration freemarkerConfig) {
        this.recordRepository = recordRepository;
        this.analyticsService = analyticsService;
        this.freemarkerConfig = freemarkerConfig;
    }

    /**
     * 异步生成 PDF 报告。
     * <p>
     * 流程：获取统计数据 → Freemarker 渲染 HTML → iText 转换 PDF
     * → 写入文件系统 → 更新数据库状态。
     * </p>
     *
     * @param recordId       报告记录 ID
     * @param templateFile   Freemarker 模板文件名（如 monthly_report.ftl）
     * @param title          报告标题
     * @param timeRangeStart 数据起始日期（可为 null）
     * @param timeRangeEnd   数据截止日期（可为 null）
     */
    @Async("reportExecutor")
    public void generate(Long recordId, String templateFile, String title,
                         java.time.LocalDate timeRangeStart, java.time.LocalDate timeRangeEnd) {
        ReportRecord record = recordRepository.findById(recordId).orElse(null);
        if (record == null) {
            log.warn("异步生成报告时找不到记录: recordId={}", recordId);
            return;
        }

        try {
            record.setStatus("GENERATING");
            recordRepository.save(record);

            // 1. 获取统计数据
            Map<String, Object> dataModel = analyticsService.calculateSnapshot();
            dataModel.put("reportTitle", title);
            dataModel.put("generateTime",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            dataModel.put("timeRangeStart",
                    timeRangeStart != null ? timeRangeStart.format(DateTimeFormatter.ISO_LOCAL_DATE) : "");
            dataModel.put("timeRangeEnd",
                    timeRangeEnd != null ? timeRangeEnd.format(DateTimeFormatter.ISO_LOCAL_DATE) : "");

            // 2. Freemarker 渲染 HTML
            Template template = freemarkerConfig.getTemplate(templateFile);
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            String html = writer.toString();

            // 3. iText HTML → PDF
            Path outputPath = Paths.get(outputDir, String.valueOf(record.getUserId()));
            Files.createDirectories(outputPath);
            String fileName = UUID.randomUUID() + ".pdf";
            Path pdfPath = outputPath.resolve(fileName);

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ConverterProperties props = new ConverterProperties();
                props.setCharset(StandardCharsets.UTF_8.name());
                HtmlConverter.convertToPdf(html, baos, props);
                Files.write(pdfPath, baos.toByteArray());
            }

            // 4. 更新记录
            record.setFilePath(pdfPath.toString());
            record.setFileSize(Files.size(pdfPath));
            record.setStatus("COMPLETED");
            recordRepository.save(record);

            log.info("报告生成完成: recordId={}, title={}, size={}", recordId, title, record.getFileSize());

        } catch (Exception e) {
            log.error("报告生成失败: recordId={}, title={}", recordId, title, e);
            record.setStatus("FAILED");
            record.setErrorMsg(e.getMessage());
            recordRepository.save(record);
        }
    }
}
