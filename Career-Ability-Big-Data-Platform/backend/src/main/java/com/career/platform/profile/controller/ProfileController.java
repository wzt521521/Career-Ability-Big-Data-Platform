package com.career.platform.profile.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.security.CurrentUser;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.profile.dto.ProfileRequest;
import com.career.platform.profile.dto.ProfileResponse;
import com.career.platform.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "学生画像", description = "学生就业画像的创建、查询与更新")
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUserProvider currentUserProvider;

    public ProfileController(ProfileService profileService,
                             CurrentUserProvider currentUserProvider) {
        this.profileService = profileService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "查询当前用户画像", description = "从 Token 中解析 user_id，返回画像对象；未创建时 data 为 null")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<ProfileResponse> getProfile() {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        ProfileResponse profile = profileService.getProfile(currentUser.getId());
        return ApiResponse.success(profile);
    }

    @PutMapping
    @Operation(summary = "创建或更新画像", description = "存在则更新，不存在则创建。user_id 从 Token 中解析，不允许客户端传入")
    @SecurityRequirement(name = "bearerAuth")
    @Log(module = "profile", operation = "save-or-update", description = "Create or update student profile")
    public ApiResponse<ProfileResponse> saveOrUpdate(@Valid @RequestBody ProfileRequest request) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        ProfileResponse profile = profileService.saveOrUpdate(currentUser.getId(), request);
        return ApiResponse.success(profile);
    }
}
