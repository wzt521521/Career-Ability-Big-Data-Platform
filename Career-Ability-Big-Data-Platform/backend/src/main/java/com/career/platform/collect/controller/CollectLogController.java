package com.career.platform.collect.controller;

import com.career.platform.collect.entity.CollectLog;
import com.career.platform.collect.service.CollectLogService;
import com.career.platform.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/collect/log")
public class CollectLogController {

    private final CollectLogService service;

    public CollectLogController(CollectLogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<CollectLog>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CollectLog> getById(@PathVariable Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping("/by-task/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<CollectLog>> listByTaskId(@PathVariable Long taskId) {
        return ApiResponse.success(service.listByTaskId(taskId));
    }
}
