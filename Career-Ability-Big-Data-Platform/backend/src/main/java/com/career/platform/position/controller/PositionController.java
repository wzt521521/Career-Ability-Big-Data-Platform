package com.career.platform.position.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.position.dto.PositionFilter;
import com.career.platform.position.dto.PositionResponse;
import com.career.platform.position.service.PositionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
public class PositionController {
    private final PositionService service;

    public PositionController(PositionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<PositionResponse>> search(PositionFilter filter) {
        return ApiResponse.success(service.search(filter));
    }

    @GetMapping("/hot")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<PositionResponse>> hot(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(service.latest(limit));
    }

    @GetMapping("/search/suggest")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<String>> suggest(@RequestParam String keyword,
                                              @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(service.suggest(keyword, limit));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PositionResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }
}
