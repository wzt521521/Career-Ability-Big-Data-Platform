package com.career.platform.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "报告生成请求")
public class GenerateReportRequest {

    @NotNull(message = "模板ID不能为空")
    @Schema(description = "报告模板ID", example = "1")
    private Long templateId;

    @NotBlank(message = "报告标题不能为空")
    @Schema(description = "报告标题", example = "2026年7月就业分析报告")
    private String title;

    @Schema(description = "数据起始日期", example = "2026-07-01")
    private LocalDate timeRangeStart;

    @Schema(description = "数据截止日期", example = "2026-07-31")
    private LocalDate timeRangeEnd;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getTimeRangeStart() { return timeRangeStart; }
    public void setTimeRangeStart(LocalDate timeRangeStart) { this.timeRangeStart = timeRangeStart; }
    public LocalDate getTimeRangeEnd() { return timeRangeEnd; }
    public void setTimeRangeEnd(LocalDate timeRangeEnd) { this.timeRangeEnd = timeRangeEnd; }
}
