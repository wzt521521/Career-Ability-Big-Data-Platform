package com.career.platform.collect.controller;

import com.career.platform.collect.dto.CollectLogQuery;
import com.career.platform.collect.dto.CollectLogResponse;
import com.career.platform.collect.dto.CollectPageRequest;
import com.career.platform.collect.service.CollectLogService;
import com.career.platform.common.ApiResponse;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/collect/log")
@Validated
public class CollectLogController {

    private final CollectLogService service;

    public CollectLogController(CollectLogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('collect:view')")
    public ApiResponse<List<CollectLogResponse>> list(@Valid CollectLogQuery query) {
        if (query.getTaskId() != null) {
            return ApiResponse.success(service.listByTaskId(query.getTaskId(), query.getPage(), query.getSize()));
        }
        return ApiResponse.success(service.list(query.getPage(), query.getSize()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collect:view')")
    public ApiResponse<CollectLogResponse> getById(
            @PathVariable @Positive(message = "执行日志 ID 必须为正整数") Long id) {
        return ApiResponse.success(service.getResponseById(id));
    }

    @GetMapping("/by-task/{taskId}")
    @PreAuthorize("hasAuthority('collect:view')")
    public ApiResponse<List<CollectLogResponse>> listByTaskId(
            @PathVariable @Positive(message = "采集任务 ID 必须为正整数") Long taskId,
            @Valid CollectPageRequest pageRequest) {
        return ApiResponse.success(service.listByTaskId(taskId, pageRequest.getPage(), pageRequest.getSize()));
    }
}
