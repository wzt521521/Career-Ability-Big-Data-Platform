package com.career.platform.auth.controller;

import com.career.platform.auth.dto.ChangePasswordRequest;
import com.career.platform.auth.dto.LoginRequest;
import com.career.platform.auth.dto.LogoutRequest;
import com.career.platform.auth.dto.RefreshTokenRequest;
import com.career.platform.auth.dto.RegisterRequest;
import com.career.platform.auth.dto.TokenResponse;
import com.career.platform.auth.dto.UpdateProfileRequest;
import com.career.platform.auth.dto.UserResponse;
import com.career.platform.auth.service.AuthService;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.ApiResponse;
import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证与个人账户", description = "注册、登录、Token 生命周期和个人资料")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "注册普通用户", description = "仅允许注册学生或教师角色")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("Registration successful", authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "账号登录", description = "返回 Access Token、Refresh Token 和当前用户权限")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新登录状态", description = "Refresh Token 单次使用，成功后轮换双 Token")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request.getRefreshToken()));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "查询当前用户")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.success(authService.getCurrentUser());
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "修改个人资料")
    @SecurityRequirement(name = "bearerAuth")
    @Log(module = "auth", operation = "update-profile", description = "Update current user profile")
    public ApiResponse<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(authService.updateProfile(request));
    }

    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "修改当前用户密码")
    @SecurityRequirement(name = "bearerAuth")
    @Log(module = "auth", operation = "change-password", description = "Change current user password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.success();
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "退出登录", description = "撤销当前 Access Token，并使对应 Refresh Token 失效")
    @SecurityRequirement(name = "bearerAuth")
    @Log(module = "auth", operation = "logout", description = "Logout current user")
    public ApiResponse<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody(required = false) LogoutRequest request) {
        String accessToken = bearerToken(authorization);
        authService.logout(accessToken, request == null ? null : request.getRefreshToken());
        return ApiResponse.success();
    }

    private String bearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authorization.substring(7);
    }
}
