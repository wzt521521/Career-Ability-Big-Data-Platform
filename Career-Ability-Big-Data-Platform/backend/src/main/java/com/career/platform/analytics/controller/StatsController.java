package com.career.platform.analytics.controller;

import com.career.platform.analytics.service.AnalyticsService;
import com.career.platform.analytics.dto.AnalyticsFilter;
import com.career.platform.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@PreAuthorize("hasAuthority('dashboard:view')")
public class StatsController {
    private final AnalyticsService analytics;

    public StatsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(AnalyticsFilter filter) { return ApiResponse.success(analytics.overviewFor(filter)); }

    @GetMapping("/positions")
    public ApiResponse<Map<String, Object>> positions(AnalyticsFilter filter) { return ApiResponse.success(analytics.positionsAnalysisFor(filter)); }

    @GetMapping("/salary")
    public ApiResponse<Map<String, Object>> salary(AnalyticsFilter filter) { return ApiResponse.success(analytics.salaryFor(filter)); }

    @GetMapping("/skills")
    public ApiResponse<Map<String, Object>> skills(AnalyticsFilter filter) { return ApiResponse.success(analytics.skillsFor(filter)); }

    @GetMapping("/education")
    public ApiResponse<Map<String, Object>> education(AnalyticsFilter filter) { return ApiResponse.success(analytics.educationFor(filter)); }

    @GetMapping("/city")
    public ApiResponse<Map<String, Object>> city(AnalyticsFilter filter) { return ApiResponse.success(analytics.cityFor(filter)); }

    @GetMapping({"/industry", "/company"})
    public ApiResponse<Map<String, Object>> company(AnalyticsFilter filter) { return ApiResponse.success(analytics.companyFor(filter)); }

    @GetMapping("/trends")
    public ApiResponse<Map<String, Object>> trends(AnalyticsFilter filter) { return ApiResponse.success(analytics.trendsFor(filter)); }
}
