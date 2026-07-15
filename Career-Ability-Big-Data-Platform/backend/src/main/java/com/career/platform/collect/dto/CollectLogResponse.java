package com.career.platform.collect.dto;

import com.career.platform.collect.entity.CollectLog;
import java.time.LocalDateTime;

/** Public representation of an immutable collection execution log. */
public class CollectLogResponse {

    private Long id;
    private Long taskId;
    private String fileName;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String errorMsg;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;

    public static CollectLogResponse from(CollectLog log) {
        CollectLogResponse response = new CollectLogResponse();
        response.id = log.getId();
        response.taskId = log.getTaskId();
        response.fileName = log.getFileName();
        response.totalCount = log.getTotalCount();
        response.successCount = log.getSuccessCount();
        response.failCount = log.getFailCount();
        response.errorMsg = log.getErrorMsg();
        response.startTime = log.getStartTime();
        response.endTime = log.getEndTime();
        response.createTime = log.getCreateTime();
        return response;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public String getFileName() { return fileName; }
    public Integer getTotalCount() { return totalCount; }
    public Integer getSuccessCount() { return successCount; }
    public Integer getFailCount() { return failCount; }
    public String getErrorMsg() { return errorMsg; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public LocalDateTime getCreateTime() { return createTime; }
}
