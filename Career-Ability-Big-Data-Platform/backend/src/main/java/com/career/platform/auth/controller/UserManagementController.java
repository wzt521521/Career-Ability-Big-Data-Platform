package com.career.platform.auth.controller;

import com.career.platform.auth.dto.AdminCreateUserRequest;
import com.career.platform.auth.dto.AdminUpdateUserRequest;
import com.career.platform.auth.dto.ResetPasswordRequest;
import com.career.platform.auth.dto.UpdateUserStatusRequest;
import com.career.platform.auth.dto.UserImportResult;
import com.career.platform.auth.dto.UserResponse;
import com.career.platform.auth.service.UserImportService;
import com.career.platform.auth.service.UserManagementService;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "用户管理", description = "管理员维护用户、角色、状态和密码")
@SecurityRequirement(name = "bearerAuth")
public class UserManagementController {

    private static final long MAX_IMPORT_SIZE = 2L * 1024 * 1024;
    private static final Set<String> EXCEL_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream");

    private final UserManagementService userManagementService;
    private final UserImportService userImportService;

    public UserManagementController(
            UserManagementService userManagementService,
            UserImportService userImportService) {
        this.userManagementService = userManagementService;
        this.userImportService = userImportService;
    }

    @GetMapping
    @Operation(summary = "分页查询用户")
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(userManagementService.list(keyword, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情")
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.success(userManagementService.get(id));
    }

    @PostMapping
    @Operation(summary = "创建用户")
    @PreAuthorize("hasAuthority('user:create')")
    @Log(module = "user", operation = "create", description = "Create system user")
    public ApiResponse<UserResponse> create(@Valid @RequestBody AdminCreateUserRequest request) {
        return ApiResponse.success(userManagementService.create(request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "批量导入用户", description = "上传模板格式的 .xlsx 文件，最多 1000 条数据")
    @PreAuthorize("hasAuthority('user:create')")
    @Log(module = "user", operation = "import", description = "Import users from Excel")
    public ApiResponse<UserImportResult> importUsers(@RequestParam("file") MultipartFile file) {
        validateImportFile(file);
        try (InputStream inputStream = file.getInputStream()) {
            return ApiResponse.success(userImportService.importUsers(inputStream));
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Unable to read uploaded file");
        }
    }

    @GetMapping("/import-template")
    @Operation(summary = "下载用户导入模板")
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<byte[]> importTemplate() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("user-import-template.xlsx")
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(userImportService.createTemplate());
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改用户")
    @PreAuthorize("hasAuthority('user:update')")
    @Log(module = "user", operation = "update", description = "Update system user")
    public ApiResponse<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        return ApiResponse.success(userManagementService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用或停用用户")
    @PreAuthorize("hasAuthority('user:update')")
    @Log(module = "user", operation = "status", description = "Change system user status")
    public ApiResponse<UserResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(userManagementService.updateStatus(id, request.getStatus()));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置用户密码")
    @PreAuthorize("hasAuthority('user:update')")
    @Log(module = "user", operation = "reset-password", description = "Reset system user password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userManagementService.resetPassword(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @PreAuthorize("hasAuthority('user:delete')")
    @Log(module = "user", operation = "delete", description = "Delete system user")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userManagementService.delete(id);
        return ApiResponse.success();
    }

    private void validateImportFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Excel file is required");
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel file exceeds the 2MB upload limit");
        }
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Only .xlsx files are supported");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !EXCEL_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported Excel content type");
        }
    }
}
