package com.career.platform.collect.controller;

import com.career.platform.collect.dto.CollectPageRequest;
import com.career.platform.collect.dto.CollectSourceRequest;
import com.career.platform.collect.dto.CollectSourceResponse;
import com.career.platform.collect.service.CollectSourceService;
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
@RequestMapping("/api/collect/source")
@Validated
public class CollectSourceController {

    private final CollectSourceService service;
    private final Validator validator;

    public CollectSourceController(CollectSourceService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('collect:view')")
    public ApiResponse<List<CollectSourceResponse>> list(@Valid CollectPageRequest pageRequest) {
        return ApiResponse.success(service.list(pageRequest.getPage(), pageRequest.getSize()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collect:view')")
    public ApiResponse<CollectSourceResponse> getById(
            @PathVariable @Positive(message = "数据源 ID 必须为正整数") Long id) {
        return ApiResponse.success(service.getResponseById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('collect:toggle')")
    public ApiResponse<CollectSourceResponse> create(
            @RequestBody CollectSourceRequest source) {
        validate(source, Default.class, CollectSourceRequest.Create.class);
        return ApiResponse.success(service.create(source));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('collect:toggle')")
    public ApiResponse<CollectSourceResponse> update(
            @PathVariable @Positive(message = "数据源 ID 必须为正整数") Long id,
            @RequestBody CollectSourceRequest source) {
        validate(source, Default.class);
        return ApiResponse.success(service.update(id, source));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('collect:toggle')")
    public ApiResponse<Void> delete(@PathVariable @Positive(message = "数据源 ID 必须为正整数") Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    private <T> void validate(T request, Class<?>... groups) {
        java.util.Set<ConstraintViolation<T>> violations = validator.validate(request, groups);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
