package com.career.platform.collect.dto;

import javax.validation.constraints.Positive;

/** Bounded filters accepted by the collection log list endpoint. */
public class CollectLogQuery extends CollectPageRequest {

    @Positive(message = "采集任务 ID 必须为正整数")
    private Long taskId;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }
}
