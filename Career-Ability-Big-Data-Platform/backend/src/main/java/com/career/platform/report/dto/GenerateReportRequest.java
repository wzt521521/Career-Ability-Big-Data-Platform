package com.career.platform.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "报告生成请求")
public class GenerateReportRequest {

    @NotNull(message = "模板ID不能为空")
    @Positive(message = "模板ID必须为正整数")
    @Schema(description = "报告模板ID", example = "1")
    private Long templateId;

    @NotBlank(message = "报告标题不能为空")
    @Size(max = 120, message = "报告标题最长120字符")
    @Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "报告标题不能包含控制字符")
    @Schema(description = "报告标题", example = "2026年7月就业分析报告")
    private String title;

    @Schema(description = "数据起始日期", example = "2026-07-01")
    private LocalDate timeRangeStart;

    @Schema(description = "数据截止日期", example = "2026-07-31")
    private LocalDate timeRangeEnd;

    @Size(max = 100, message = "城市筛选最长100字符")
    private String city;

    @Size(max = 100, message = "岗位筛选最长100字符")
    private String position;

    @Size(max = 100, message = "行业筛选最长100字符")
    private String industry;

    @javax.validation.constraints.AssertTrue(message = "开始日期不能晚于结束日期")
    public boolean isTimeRangeValid() {
        return timeRangeStart == null || timeRangeEnd == null || !timeRangeStart.isAfter(timeRangeEnd);
    }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getTimeRangeStart() { return timeRangeStart; }
    public void setTimeRangeStart(LocalDate timeRangeStart) { this.timeRangeStart = timeRangeStart; }
    public LocalDate getTimeRangeEnd() { return timeRangeEnd; }
    public void setTimeRangeEnd(LocalDate timeRangeEnd) { this.timeRangeEnd = timeRangeEnd; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
}
