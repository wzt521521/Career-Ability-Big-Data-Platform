package com.career.platform.position.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.position.dto.PositionFilter;
import com.career.platform.position.dto.PositionResponse;
import com.career.platform.position.service.PositionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@RestController
@RequestMapping("/api/positions")
@Validated
public class PositionController {
    private final PositionService service;

    public PositionController(PositionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('position:view')")
    public ApiResponse<PageResponse<PositionResponse>> search(@Valid PositionFilter filter) {
        return ApiResponse.success(service.searchPublicRecruitment(filter));
    }

    @GetMapping("/hot")
    @PreAuthorize("hasAuthority('position:view')")
    public ApiResponse<List<PositionResponse>> hot(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.success(service.latestPublicRecruitment(limit));
    }

    @GetMapping("/search/suggest")
    @PreAuthorize("hasAuthority('position:view')")
    public ApiResponse<List<String>> suggest(@RequestParam @NotBlank @Size(max = 100) String keyword,
                                              @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return ApiResponse.success(service.suggestPublicTitles(keyword, limit));
    }

    @GetMapping("/compare")
    @PreAuthorize("hasAuthority('position:view')")
    public ApiResponse<List<PositionResponse>> compare(@RequestParam List<Long> ids) {
        return ApiResponse.success(service.comparePublicRecruitment(ids));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('position:view')")
    public ApiResponse<PositionResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.getPublicRecruitment(id));
    }
}
