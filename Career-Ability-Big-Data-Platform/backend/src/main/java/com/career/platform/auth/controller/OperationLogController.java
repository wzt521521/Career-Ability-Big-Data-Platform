package com.career.platform.auth.controller;

import com.career.platform.auth.dto.OperationLogResponse;
import com.career.platform.auth.service.OperationLogService;
import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/operation-logs")
@Tag(name = "操作日志", description = "查询关键管理操作的审计记录")
@SecurityRequirement(name = "bearerAuth")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    @Operation(summary = "分页查询操作日志")
    @PreAuthorize("hasAuthority('log:read')")
    public ApiResponse<PageResponse<OperationLogResponse>> list(
            @RequestParam(defaultValue = "") @Size(max = 50) String username,
            @RequestParam(defaultValue = "") @Size(max = 50) String module,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(operationLogService.list(username, module, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询操作日志详情")
    @PreAuthorize("hasAuthority('log:read')")
    public ApiResponse<OperationLogResponse> get(@PathVariable Long id) {
        return ApiResponse.success(operationLogService.get(id));
    }
}
