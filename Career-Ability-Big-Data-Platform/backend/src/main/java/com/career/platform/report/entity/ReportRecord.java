package com.career.platform.report.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_record")
public class ReportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "report_title", length = 200, nullable = false)
    private String reportTitle;

    @Column(name = "time_range_start")
    private LocalDate timeRangeStart;

    @Column(name = "time_range_end")
    private LocalDate timeRangeEnd;

    @Column(name = "filter_city", length = 100)
    private String filterCity;

    @Column(name = "filter_position", length = 100)
    private String filterPosition;

    @Column(name = "filter_industry", length = 100)
    private String filterIndustry;

    @Column(name = "analysis_dimensions", length = 500)
    private String analysisDimensions;

    @Column(name = "analysis_scope", columnDefinition = "TEXT")
    private String analysisScope;

    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "generation_started_at")
    private LocalDateTime generationStartedAt;

    @Column(name = "generation_attempts", nullable = false)
    private Integer generationAttempts = 0;

    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", insertable = false, updatable = false)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getReportTitle() { return reportTitle; }
    public void setReportTitle(String reportTitle) { this.reportTitle = reportTitle; }
    public LocalDate getTimeRangeStart() { return timeRangeStart; }
    public void setTimeRangeStart(LocalDate timeRangeStart) { this.timeRangeStart = timeRangeStart; }
    public LocalDate getTimeRangeEnd() { return timeRangeEnd; }
    public void setTimeRangeEnd(LocalDate timeRangeEnd) { this.timeRangeEnd = timeRangeEnd; }
    public String getFilterCity() { return filterCity; }
    public void setFilterCity(String filterCity) { this.filterCity = filterCity; }
    public String getFilterPosition() { return filterPosition; }
    public void setFilterPosition(String filterPosition) { this.filterPosition = filterPosition; }
    public String getFilterIndustry() { return filterIndustry; }
    public void setFilterIndustry(String filterIndustry) { this.filterIndustry = filterIndustry; }
    public String getAnalysisDimensions() { return analysisDimensions; }
    public void setAnalysisDimensions(String analysisDimensions) { this.analysisDimensions = analysisDimensions; }
    public String getAnalysisScope() { return analysisScope; }
    public void setAnalysisScope(String analysisScope) { this.analysisScope = analysisScope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public LocalDateTime getGenerationStartedAt() { return generationStartedAt; }
    public void setGenerationStartedAt(LocalDateTime generationStartedAt) { this.generationStartedAt = generationStartedAt; }
    public Integer getGenerationAttempts() { return generationAttempts; }
    public void setGenerationAttempts(Integer generationAttempts) { this.generationAttempts = generationAttempts; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
