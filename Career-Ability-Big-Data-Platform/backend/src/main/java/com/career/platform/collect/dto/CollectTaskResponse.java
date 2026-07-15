package com.career.platform.collect.dto;

import com.career.platform.collect.entity.CollectTask;
import java.time.LocalDateTime;

/** Public representation of a collection task. */
public class CollectTaskResponse {

    private Long id;
    private Long sourceId;
    private String taskName;
    private String cronExpression;
    private String status;
    private LocalDateTime lastRunTime;
    private LocalDateTime nextRunTime;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static CollectTaskResponse from(CollectTask task) {
        CollectTaskResponse response = new CollectTaskResponse();
        response.id = task.getId();
        response.sourceId = task.getSourceId();
        response.taskName = task.getTaskName();
        response.cronExpression = task.getCronExpression();
        response.status = task.getStatus();
        response.lastRunTime = task.getLastRunTime();
        response.nextRunTime = task.getNextRunTime();
        response.retryCount = task.getRetryCount();
        response.maxRetries = task.getMaxRetries();
        response.createTime = task.getCreateTime();
        response.updateTime = task.getUpdateTime();
        return response;
    }

    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public String getTaskName() { return taskName; }
    public String getCronExpression() { return cronExpression; }
    public String getStatus() { return status; }
    public LocalDateTime getLastRunTime() { return lastRunTime; }
    public LocalDateTime getNextRunTime() { return nextRunTime; }
    public Integer getRetryCount() { return retryCount; }
    public Integer getMaxRetries() { return maxRetries; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
