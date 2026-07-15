package com.career.platform.openapi.controller;

import com.career.platform.analytics.dto.AnalyticsDimension;
import com.career.platform.analytics.dto.AnalyticsSnapshotRequest;
import com.career.platform.analytics.dto.AnalyticsSnapshotResponse;
import com.career.platform.analytics.service.AnalyticsService;
import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.openapi.security.OpenApiRequestContext;
import com.career.platform.position.dto.PositionFilter;
import com.career.platform.position.dto.PositionResponse;
import com.career.platform.position.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versioned read-only API for the public recruitment dataset.
 *
 * <p>College and self filters are deliberately absent: recruitment records have no college or
 * owner dimension. The returned snapshot carries {@code PUBLIC_RECRUITMENT} as its data scope.
 * </p>
 */
@Validated
@RestController
@RequestMapping("/api/open/v1")
@Tag(name = "开放招聘数据 API", description = "公开招聘岗位与聚合分析；每个请求均需要匹配的 Bearer Token 和 X-API-Key")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "apiKeyAuth")
@PreAuthorize("isAuthenticated()")
public class OpenRecruitmentController {

    private final PositionService positionService;
    private final AnalyticsService analyticsService;

    public OpenRecruitmentController(PositionService positionService, AnalyticsService analyticsService) {
        this.positionService = positionService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/positions")
    @Operation(summary = "分页查询公开招聘岗位", description = "page 从 1 开始，最多 100 条；不支持学院或用户维度筛选")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "岗位分页结果", headers = {
                    @Header(name = "X-RateLimit-Limit", description = "当前 API Key 每分钟配额", schema = @Schema(type = "integer")),
                    @Header(name = "X-RateLimit-Remaining", description = "剩余请求数", schema = @Schema(type = "integer"))}),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "缺少或无效的 Bearer Token/API Key"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "API Key 不属于当前 JWT 用户或已停用"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "已达到 API Key 限流")
    })
    public ApiResponse<PageResponse<PositionResponse>> positions(@Valid PositionFilter filter,
            HttpServletRequest request) {
        requireCaller(request);
        return ApiResponse.success(positionService.searchPublicRecruitment(filter));
    }

    @GetMapping("/skills/hot")
    @Operation(summary = "查询热门技能", description = "按公开招聘记录聚合热门技能")
    public ApiResponse<AnalyticsSnapshotResponse> hotSkills(
            @Parameter(in = ParameterIn.QUERY, description = "返回条数上限，由客户端用于裁剪结果")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        requireCaller(request);
        AnalyticsSnapshotRequest snapshot = snapshot(AnalyticsDimension.SKILL);
        return ApiResponse.success(limitedSnapshot(
                analyticsService.publicSnapshot(snapshot), "skills", "topSkills", limit));
    }

    @GetMapping("/cities/ranking")
    @Operation(summary = "查询城市岗位排行", description = "按公开招聘记录聚合城市排行")
    public ApiResponse<AnalyticsSnapshotResponse> cityRanking(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        requireCaller(request);
        return ApiResponse.success(limitedSnapshot(
                analyticsService.publicSnapshot(snapshot(AnalyticsDimension.CITY)), "city", "ranking", limit));
    }

    @GetMapping("/salary/trends")
    @Operation(summary = "查询薪资与招聘趋势", description = "日期边界为包含关系；未提供边界时使用完整公开招聘历史")
    public ApiResponse<AnalyticsSnapshotResponse> salaryAndTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request) {
        requireCaller(request);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
        AnalyticsSnapshotRequest snapshot = snapshot(AnalyticsDimension.SALARY, AnalyticsDimension.TREND);
        snapshot.setStartDate(startDate);
        snapshot.setEndDate(endDate);
        return ApiResponse.success(analyticsService.publicSnapshot(snapshot));
    }

    private void requireCaller(HttpServletRequest request) {
        OpenApiRequestContext.requireCaller(request);
    }

    private AnalyticsSnapshotRequest snapshot(AnalyticsDimension... dimensions) {
        AnalyticsSnapshotRequest request = new AnalyticsSnapshotRequest();
        request.setDimensions(EnumSet.copyOf(java.util.Arrays.asList(dimensions)));
        return request;
    }

    @SuppressWarnings("unchecked")
    private AnalyticsSnapshotResponse limitedSnapshot(
            AnalyticsSnapshotResponse snapshot, String dimension, String listField, int limit) {
        Map<String, Object> data = new LinkedHashMap<>(snapshot.getData());
        Object dimensionData = data.get(dimension);
        if (dimensionData instanceof Map<?, ?>) {
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) dimensionData);
            Object ranked = copy.get(listField);
            if (ranked instanceof List<?>) {
                List<?> values = (List<?>) ranked;
                copy.put(listField, List.copyOf(values.subList(0, Math.min(limit, values.size()))));
            }
            data.put(dimension, copy);
        }
        return new AnalyticsSnapshotResponse(
                snapshot.getScope(),
                snapshot.getStartDate(),
                snapshot.getEndDate(),
                snapshot.getDimensions(),
                data,
                snapshot.isEmpty(),
                snapshot.getGeneratedAt());
    }
}
