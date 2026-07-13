package com.career.platform.report.dto;

import com.career.platform.report.entity.ReportRecord;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "报告记录响应")
public class ReportRecordResponse {

    @Schema(description = "报告ID")
    private final Long id;

    @Schema(description = "模板ID")
    private final Long templateId;

    @Schema(description = "模板名称")
    private final String templateName;

    @Schema(description = "报告标题")
    private final String reportTitle;

    @Schema(description = "数据起始日期")
    private final LocalDate timeRangeStart;

    @Schema(description = "数据截止日期")
    private final LocalDate timeRangeEnd;

    @Schema(description = "状态：PENDING/GENERATING/COMPLETED/FAILED")
    private final String status;

    @Schema(description = "文件大小（字节）")
    private final Long fileSize;

    @Schema(description = "错误信息")
    private final String errorMsg;

    @Schema(description = "创建时间")
    private final LocalDateTime createTime;

    @Schema(description = "更新时间")
    private final LocalDateTime updateTime;

    public ReportRecordResponse(ReportRecord record) {
        this(record, null);
    }

    public ReportRecordResponse(ReportRecord record, String templateName) {
        this.id = record.getId();
        this.templateId = record.getTemplateId();
        this.templateName = templateName;
        this.reportTitle = record.getReportTitle();
        this.timeRangeStart = record.getTimeRangeStart();
        this.timeRangeEnd = record.getTimeRangeEnd();
        this.status = record.getStatus();
        this.fileSize = record.getFileSize();
        this.errorMsg = record.getErrorMsg();
        this.createTime = record.getCreateTime();
        this.updateTime = record.getUpdateTime();
    }

    public Long getId() { return id; }
    public Long getTemplateId() { return templateId; }
    public String getTemplateName() { return templateName; }
    public String getReportTitle() { return reportTitle; }
    public LocalDate getTimeRangeStart() { return timeRangeStart; }
    public LocalDate getTimeRangeEnd() { return timeRangeEnd; }
    public String getStatus() { return status; }
    public Long getFileSize() { return fileSize; }
    public String getErrorMsg() { return errorMsg; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
