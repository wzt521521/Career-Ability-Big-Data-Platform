package com.career.platform.collect.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Locale;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import org.springframework.scheduling.support.CronExpression;

/** Mutable task configuration accepted by the collection API. */
@JsonIgnoreProperties({"id", "retryCount", "lastRunTime", "nextRunTime", "createTime", "updateTime"})
public class CollectTaskRequest {

    public interface Create {
    }

    @NotNull(groups = Create.class, message = "关联数据源不能为空")
    @Positive(message = "关联数据源必须为正整数")
    private Long sourceId;

    @NotBlank(groups = Create.class, message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称不能超过 100 个字符")
    private String taskName;

    @Size(max = 50, message = "Cron 表达式不能超过 50 个字符")
    private String cronExpression;

    @Pattern(regexp = "(?i)^(IDLE|SCHEDULED|RUNNING|PAUSED|FAILED|ERROR)$",
            message = "任务状态只能是 IDLE、SCHEDULED、RUNNING、PAUSED、FAILED 或 ERROR")
    private String status;

    @Min(value = 0, message = "最大重试次数不能小于 0")
    @Max(value = 3, message = "最大重试次数不能超过 3")
    private Integer maxRetries;

    @AssertTrue(message = "Cron 表达式不合法")
    @JsonIgnore
    public boolean isCronExpressionValid() {
        return cronExpression == null || cronExpression.isBlank()
                || CronExpression.isValidExpression(cronExpression);
    }

    public String normalizedStatus() {
        return status == null ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isSupportedStatus(String status) {
        if (status == null) {
            return false;
        }
        switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "IDLE":
            case "SCHEDULED":
            case "RUNNING":
            case "PAUSED":
            case "FAILED":
            case "ERROR":
                return true;
            default:
                return false;
        }
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }
}
