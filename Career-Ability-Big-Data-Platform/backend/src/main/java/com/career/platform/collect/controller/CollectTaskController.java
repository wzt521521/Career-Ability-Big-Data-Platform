package com.career.platform.collect.controller;

import com.career.platform.collect.dto.CollectPageRequest;
import com.career.platform.collect.dto.CollectTaskRequest;
import com.career.platform.collect.dto.CollectTaskResponse;
import com.career.platform.collect.dto.CollectLogResponse;
import com.career.platform.collect.service.CollectTaskRuntimeService;
import com.career.platform.collect.service.CollectTaskService;
import com.career.platform.common.ApiResponse;
import javax.validation.Valid;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.validation.constraints.Positive;
import javax.validation.groups.Default;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/collect/task")
@Validated
public class CollectTaskController {

    private final CollectTaskService service;
    private final CollectTaskRuntimeService runtimeService;
    private final Validator validator;

    public CollectTaskController(
            CollectTaskService service,
            CollectTaskRuntimeService runtimeService,
            Validator validator) {
        this.service = service;
        this.runtimeService = runtimeService;
        this.validator = validator;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('collect:view')")
    public ApiResponse<List<CollectTaskResponse>> list(@Valid CollectPageRequest pageRequest) {
        return ApiResponse.success(service.list(pageRequest.getPage(), pageRequest.getSize()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collect:view')")
    public ApiResponse<CollectTaskResponse> getById(
            @PathVariable @Positive(message = "采集任务 ID 必须为正整数") Long id) {
        return ApiResponse.success(service.getResponseById(id));
    }

    @GetMapping("/by-source/{sourceId}")
    @PreAuthorize("hasAuthority('collect:view')")
    public ApiResponse<List<CollectTaskResponse>> listBySourceId(
            @PathVariable @Positive(message = "数据源 ID 必须为正整数") Long sourceId,
            @Valid CollectPageRequest pageRequest) {
        return ApiResponse.success(service.listBySourceId(sourceId, pageRequest.getPage(), pageRequest.getSize()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('collect:toggle')")
    public ApiResponse<CollectTaskResponse> create(
            @RequestBody CollectTaskRequest task) {
        validate(task, Default.class, CollectTaskRequest.Create.class);
        return ApiResponse.success(service.create(task));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('collect:toggle')")
    public ApiResponse<CollectTaskResponse> update(
            @PathVariable @Positive(message = "采集任务 ID 必须为正整数") Long id,
            @RequestBody CollectTaskRequest task) {
        validate(task, Default.class);
        return ApiResponse.success(service.update(id, task));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('collect:toggle')")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "采集任务 ID 必须为正整数") Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('collect:toggle')")
    public ApiResponse<CollectTaskResponse> run(
            @PathVariable @Positive(message = "采集任务 ID 必须为正整数") Long id) {
        return ApiResponse.success(runtimeService.run(id));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('collect:toggle')")
    public ApiResponse<CollectTaskResponse> pause(
            @PathVariable @Positive(message = "采集任务 ID 必须为正整数") Long id) {
        return ApiResponse.success(runtimeService.pause(id));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('collect:toggle')")
    public ApiResponse<CollectTaskResponse> resume(
            @PathVariable @Positive(message = "采集任务 ID 必须为正整数") Long id) {
        return ApiResponse.success(runtimeService.resume(id));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('collect:view')")
    public ApiResponse<List<CollectLogResponse>> listLogs(
            @PathVariable @Positive(message = "采集任务 ID 必须为正整数") Long id,
            @Valid CollectPageRequest pageRequest) {
        return ApiResponse.success(runtimeService.listLogs(id, pageRequest.getPage(), pageRequest.getSize()));
    }

    private <T> void validate(T request, Class<?>... groups) {
        java.util.Set<ConstraintViolation<T>> violations = validator.validate(request, groups);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
