package com.career.platform.collect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "collect_task")
public class CollectTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    @Column(name = "cron_expression", length = 50)
    private String cronExpression;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "last_run_time")
    private LocalDateTime lastRunTime;

    @Column(name = "next_run_time")
    private LocalDateTime nextRunTime;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", insertable = false, updatable = false)
    private LocalDateTime updateTime;

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastRunTime() { return lastRunTime; }
    public void setLastRunTime(LocalDateTime lastRunTime) { this.lastRunTime = lastRunTime; }
    public LocalDateTime getNextRunTime() { return nextRunTime; }
    public void setNextRunTime(LocalDateTime nextRunTime) { this.nextRunTime = nextRunTime; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
