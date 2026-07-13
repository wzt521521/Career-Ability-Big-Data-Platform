package com.career.platform.recommend.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.recommend.dto.GapAnalysisResponse;
import com.career.platform.recommend.dto.RecommendationResponse;
import com.career.platform.recommend.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommend")
@Tag(name = "岗位推荐", description = "基于学生画像的个性化岗位推荐与技能差距分析")
public class RecommendController {

    private final RecommendService recommendService;
    private final CurrentUserProvider currentUserProvider;

    public RecommendController(RecommendService recommendService,
                              CurrentUserProvider currentUserProvider) {
        this.recommendService = recommendService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('recommend:view')")
    @Operation(summary = "获取个性化岗位推荐", description = "基于当前用户的就业画像，计算与全部岗位的五维加权匹配得分，返回TOP20推荐结果")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<PageResponse<RecommendationResponse>> recommend(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        List<RecommendationResponse> content = recommendService.recommend(userId, page, size);
        long total = recommendService.count(userId);
        int totalPages = (int) Math.ceil((double) total / size);
        return ApiResponse.success(new PageResponse<>(content, total, totalPages, page, size));
    }

    @GetMapping("/{positionId}/gap-analysis")
    @PreAuthorize("hasAuthority('recommend:view')")
    @Operation(summary = "技能差距分析", description = "对比学生技能与指定岗位要求，展示匹配/缺失技能及学习建议")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<GapAnalysisResponse> gapAnalysis(@PathVariable Long positionId) {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        return ApiResponse.success(recommendService.gapAnalysis(userId, positionId));
    }
}
