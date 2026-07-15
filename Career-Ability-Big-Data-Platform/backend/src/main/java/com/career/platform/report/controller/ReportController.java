package com.career.platform.report.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.report.dto.GenerateReportRequest;
import com.career.platform.report.dto.ReportRecordResponse;
import com.career.platform.report.dto.ReportTemplateResponse;
import com.career.platform.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "报告中心", description = "就业分析报告的生成、查询、下载与删除")
@Validated
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserProvider currentUserProvider;

    public ReportController(ReportService reportService,
                           CurrentUserProvider currentUserProvider) {
        this.reportService = reportService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "获取可用报告模板", description = "返回所有启用状态的报告模板列表")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<List<ReportTemplateResponse>> getTemplates() {
        return ApiResponse.success(reportService.getTemplates());
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('report:generate')")
    @Operation(summary = "异步生成报告", description = "提交生成请求后立即返回PENDING状态，后台异步生成PDF，前端轮询状态")
    @SecurityRequirement(name = "bearerAuth")
    @Log(module = "report", operation = "generate", description = "Generate a PDF report")
    public ApiResponse<ReportRecordResponse> generate(@Valid @RequestBody GenerateReportRequest request) {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        return ApiResponse.success(reportService.generate(userId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "查询报告列表", description = "分页查询当前用户的报告记录，可按状态筛选、关键字搜索标题")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<PageResponse<ReportRecordResponse>> list(
            @RequestParam(required = false) @Pattern(regexp = "^(|PENDING|GENERATING|COMPLETED|FAILED)$",
                    message = "不支持的报告状态") String status,
            @RequestParam(required = false) @javax.validation.constraints.Size(max = 100,
                    message = "报告标题关键字最长100字符") String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size 必须大于等于 1")
            @Max(value = 100, message = "size 不能超过 100") int size) {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        return ApiResponse.success(reportService.listRecords(userId, status, keyword, page, size));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "查询报告生成状态", description = "用于前端轮询报告生成进度")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<ReportRecordResponse> status(@PathVariable @Positive Long id) {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        return ApiResponse.success(reportService.getStatus(userId, id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "下载报告PDF文件")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Resource> download(@PathVariable @Positive Long id) {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        Resource resource = reportService.download(userId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report-" + id + ".pdf\"")
                .body(resource);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "在线预览报告PDF", description = "与下载使用相同文件，但浏览器内联展示而非强制下载")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Resource> preview(@PathVariable @Positive Long id) {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        Resource resource = reportService.download(userId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"report-" + id + ".pdf\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('report:delete')")
    @Operation(summary = "删除报告", description = "删除报告记录及其PDF文件")
    @SecurityRequirement(name = "bearerAuth")
    @Log(module = "report", operation = "delete", description = "Delete a report record")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        reportService.delete(userId, id);
        return ApiResponse.success();
    }
}
