package com.career.platform.profile.service;

import com.career.platform.profile.dto.ProfileRequest;
import com.career.platform.profile.dto.ProfileResponse;

public interface ProfileService {

    /**
     * 查询当前用户的画像
     *
     * @param userId 用户ID
     * @return 画像对象，未创建时返回 null
     */
    ProfileResponse getProfile(Long userId);

    /**
     * 创建或更新用户画像
     *
     * @param userId  用户ID
     * @param request 画像数据
     * @return 保存后的画像
     */
    ProfileResponse saveOrUpdate(Long userId, ProfileRequest request);
}
