package com.career.platform.openapi.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.openapi.dto.ApiKeyResponse;
import com.career.platform.openapi.dto.CreateApiKeyRequest;
import com.career.platform.openapi.dto.CreatedApiKeyResponse;
import com.career.platform.openapi.dto.UpdateApiKeyStatusRequest;
import com.career.platform.openapi.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/api-keys")
@Tag(name = "API Key 管理", description = "创建、查询、停用和删除第三方调用凭证")
@SecurityRequirement(name = "bearerAuth")
public class ApiKeyManagementController {

    private final ApiKeyService apiKeyService;

    public ApiKeyManagementController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    @Operation(summary = "分页查询 API Key")
    @PreAuthorize("hasAuthority('api:key:manage')")
    public ApiResponse<PageResponse<ApiKeyResponse>> list(
            @RequestParam(defaultValue = "") @Size(max = 100) String appName,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(apiKeyService.list(appName, page, size));
    }

    @PostMapping
    @Operation(summary = "创建 API Key", description = "密钥原文仅在本次响应中返回")
    @PreAuthorize("hasAuthority('api:key:manage')")
    @Log(module = "open-api", operation = "create-key", description = "Create API key")
    public ApiResponse<CreatedApiKeyResponse> create(
            @Valid @RequestBody CreateApiKeyRequest request) {
        return ApiResponse.success(apiKeyService.create(request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用或停用 API Key")
    @PreAuthorize("hasAuthority('api:key:manage')")
    @Log(module = "open-api", operation = "key-status", description = "Change API key status")
    public ApiResponse<ApiKeyResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApiKeyStatusRequest request) {
        return ApiResponse.success(apiKeyService.updateStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 API Key")
    @PreAuthorize("hasAuthority('api:key:manage')")
    @Log(module = "open-api", operation = "delete-key", description = "Delete API key")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        apiKeyService.delete(id);
        return ApiResponse.success();
    }
}
