package com.career.platform.profile.service.impl;

import com.career.platform.profile.dto.ProfileRequest;
import com.career.platform.profile.dto.ProfileResponse;
import com.career.platform.profile.entity.StudentProfile;
import com.career.platform.profile.repository.ProfileRepository;
import com.career.platform.profile.service.ProfileService;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;
    private final CacheManager cacheManager;

    public ProfileServiceImpl(ProfileRepository profileRepository,
                             CacheManager cacheManager) {
        this.profileRepository = profileRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .map(ProfileResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional
    public ProfileResponse saveOrUpdate(Long userId, ProfileRequest request) {
        StudentProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    StudentProfile newProfile = new StudentProfile();
                    newProfile.setUserId(userId);
                    return newProfile;
                });

        // 只更新请求中提供的字段（非 null 字段）
        if (request.getMajor() != null) {
            profile.setMajor(request.getMajor());
        }
        if (request.getSkills() != null) {
            profile.setSkills(request.getSkills());
        }
        if (request.getEducation() != null) {
            profile.setEducation(request.getEducation());
        }
        if (request.getPreferredCity() != null) {
            profile.setPreferredCity(request.getPreferredCity());
        }
        if (request.getSalaryMin() != null) {
            profile.setSalaryMin(request.getSalaryMin());
        }
        if (request.getSalaryMax() != null) {
            profile.setSalaryMax(request.getSalaryMax());
        }

        ProfileResponse response = ProfileResponse.from(profileRepository.save(profile));

        // 画像更新后清除该用户的推荐缓存（保证下次获取推荐时重新计算）
        var cache = cacheManager.getCache("recommend");
        if (cache != null) {
            cache.evict(userId);
        }

        return response;
    }
}
