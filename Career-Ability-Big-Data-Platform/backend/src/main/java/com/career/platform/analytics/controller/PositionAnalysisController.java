package com.career.platform.analytics.controller;

import com.career.platform.analytics.service.AnalyticsService;
import com.career.platform.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@PreAuthorize("isAuthenticated()")
public class PositionAnalysisController {
    private final AnalyticsService analytics;

    public PositionAnalysisController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/salary-distribution")
    public ApiResponse<Object> salary(@RequestParam(required = false) String position) {
        return ApiResponse.success(analytics.salaryForTitle(position));
    }

    @GetMapping("/skills")
    public ApiResponse<Object> skills(@RequestParam(required = false) String position) {
        return ApiResponse.success(analytics.skillsForTitle(position));
    }

    @GetMapping("/city-ranking")
    public ApiResponse<Object> city(@RequestParam(required = false) String position) {
        return ApiResponse.success(analytics.cityForTitle(position));
    }

    @GetMapping("/education")
    public ApiResponse<Object> education(@RequestParam(required = false) String position) {
        return ApiResponse.success(analytics.educationForTitle(position));
    }
}
