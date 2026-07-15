package com.career.platform.openapi.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import com.career.platform.common.security.CurrentUser;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.openapi.security.OpenApiCaller;
import com.career.platform.openapi.security.OpenApiRequestContext;
import com.career.platform.profile.dto.ProfileResponse;
import com.career.platform.profile.service.ProfileService;
import com.career.platform.recommend.dto.GapAnalysisResponse;
import com.career.platform.recommend.dto.RecommendationResponse;
import com.career.platform.recommend.service.RecommendService;
import com.career.platform.report.dto.ReportRecordResponse;
import com.career.platform.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open API views that are deliberately restricted to the authenticated API-key owner. There is
 * no userId route, path, or query parameter: caller identity is supplied by the API-key filter
 * and cross-checked against the JWT principal before service methods receive it.
 */
@Validated
@RestController
@RequestMapping("/api/open/v1")
@Tag(name = "开放个人数据 API", description = "当前 API Key 所属用户的画像、推荐和报告只读数据")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "apiKeyAuth")
@PreAuthorize("isAuthenticated()")
public class OpenPersonalDataController {

    private final CurrentUserProvider currentUserProvider;
    private final ProfileService profileService;
    private final RecommendService recommendService;
    private final ReportService reportService;

    public OpenPersonalDataController(CurrentUserProvider currentUserProvider,
                                      ProfileService profileService,
                                      RecommendService recommendService,
                                      ReportService reportService) {
        this.currentUserProvider = currentUserProvider;
        this.profileService = profileService;
        this.recommendService = recommendService;
        this.reportService = reportService;
    }

    @GetMapping("/profile")
    @Operation(summary = "读取当前调用方画像", description = "仅返回 Bearer Token 与 X-API-Key 共同所属用户的画像")
    public ApiResponse<ProfileResponse> profile(HttpServletRequest request) {
        return ApiResponse.success(profileService.getProfile(requireOwner(request)));
    }

    @GetMapping("/recommendations")
    @PreAuthorize("hasAuthority('recommend:view')")
    @Operation(summary = "读取当前调用方推荐", description = "返回当前调用方的全局 TOP20 推荐分页")
    public ApiResponse<PageResponse<RecommendationResponse>> recommendations(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size,
            HttpServletRequest request) {
        Long userId = requireOwner(request);
        java.util.List<RecommendationResponse> content = recommendService.recommend(userId, page, size);
        long total = recommendService.count(userId);
        int totalPages = (int) Math.ceil((double) total / size);
        return ApiResponse.success(new PageResponse<>(content, total, totalPages, page, size));
    }

    @GetMapping("/recommendations/gap")
    @PreAuthorize("hasAuthority('recommend:view')")
    @Operation(summary = "读取当前调用方技能差距", description = "positionId 必须为正整数，用户由认证上下文确定")
    public ApiResponse<GapAnalysisResponse> recommendationGap(
            @RequestParam @Positive Long positionId,
            HttpServletRequest request) {
        return ApiResponse.success(recommendService.gapAnalysis(requireOwner(request), positionId));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "读取当前调用方报告列表", description = "仅返回当前 API Key 所属用户的报告")
    public ApiResponse<PageResponse<ReportRecordResponse>> reports(
            @RequestParam(required = false) @Pattern(regexp = "^(|PENDING|GENERATING|COMPLETED|FAILED)$") String status,
            @RequestParam(required = false) @javax.validation.constraints.Size(max = 100) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(reportService.listRecords(requireOwner(request), status, keyword, page, size));
    }

    @GetMapping("/reports/{id}/download")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "下载当前调用方已完成报告")
    public ResponseEntity<Resource> downloadReport(@PathVariable @Positive Long id, HttpServletRequest request) {
        Resource resource = reportService.download(requireOwner(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + id + ".pdf\"")
                .body(resource);
    }

    private Long requireOwner(HttpServletRequest request) {
        OpenApiCaller caller = OpenApiRequestContext.requireCaller(request);
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        if (caller.getUserId() == null || !caller.getUserId().equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Open API caller does not match the authenticated user");
        }
        return currentUser.getId();
    }
}
