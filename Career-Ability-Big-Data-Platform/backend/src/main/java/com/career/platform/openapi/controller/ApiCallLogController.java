package com.career.platform.openapi.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.openapi.dto.ApiCallLogResponse;
import com.career.platform.openapi.dto.ApiCallStatisticsResponse;
import com.career.platform.openapi.service.ApiCallLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
@Tag(name = "开放接口统计", description = "第三方接口调用日志和聚合统计")
@SecurityRequirement(name = "bearerAuth")
public class ApiCallLogController {

    private final ApiCallLogService callLogService;

    public ApiCallLogController(ApiCallLogService callLogService) {
        this.callLogService = callLogService;
    }

    @GetMapping("/api-call-logs")
    @Operation(summary = "分页查询 API 调用日志")
    @PreAuthorize("hasAuthority('api:view')")
    public ApiResponse<PageResponse<ApiCallLogResponse>> list(
            @RequestParam(defaultValue = "") @Size(max = 200) String path,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(callLogService.list(path, page, size));
    }

    @GetMapping("/api-call-statistics")
    @Operation(summary = "查询 API 调用统计")
    @PreAuthorize("hasAuthority('api:view')")
    public ApiResponse<ApiCallStatisticsResponse> statistics() {
        return ApiResponse.success(callLogService.statistics());
    }
}
