package com.career.platform.collect.controller;

import com.career.platform.collect.entity.CollectSource;
import com.career.platform.collect.service.CollectSourceService;
import com.career.platform.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/collect/source")
public class CollectSourceController {

    private final CollectSourceService service;

    public CollectSourceController(CollectSourceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<CollectSource>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CollectSource> getById(@PathVariable Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CollectSource> create(@RequestBody CollectSource source) {
        return ApiResponse.success(service.create(source));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CollectSource> update(@PathVariable Long id, @RequestBody CollectSource source) {
        return ApiResponse.success(service.update(id, source));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
