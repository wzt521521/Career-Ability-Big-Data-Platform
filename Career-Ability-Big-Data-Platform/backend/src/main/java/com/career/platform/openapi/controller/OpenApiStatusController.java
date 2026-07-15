package com.career.platform.openapi.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.openapi.security.OpenApiRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/open/v1/platform")
@Tag(name = "开放 API", description = "需要同时携带 Bearer Token 与 X-API-Key")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "apiKeyAuth")
@PreAuthorize("isAuthenticated()")
public class OpenApiStatusController {

    @GetMapping("/status")
    @Operation(summary = "查询开放接口状态", description = "用于验证 API Key、JWT、限流和审计完整链路")
    public ApiResponse<Map<String, Object>> status(HttpServletRequest request) {
        OpenApiRequestContext.requireCaller(request);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "career-ability-platform");
        status.put("status", "available");
        status.put("time", Instant.now().toString());
        return ApiResponse.success(status);
    }
}
