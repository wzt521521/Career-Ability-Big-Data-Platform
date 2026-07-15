package com.career.platform.profile.service.impl;

import com.career.platform.profile.dto.ProfileRequest;
import com.career.platform.profile.dto.ProfileResponse;
import com.career.platform.profile.entity.StudentProfile;
import com.career.platform.profile.repository.ProfileRepository;
import com.career.platform.profile.service.ProfileService;
import com.career.platform.recommend.service.RecommendationCacheInvalidator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;
    private final RecommendationCacheInvalidator recommendationCacheInvalidator;

    public ProfileServiceImpl(ProfileRepository profileRepository,
                              RecommendationCacheInvalidator recommendationCacheInvalidator) {
        this.profileRepository = profileRepository;
        this.recommendationCacheInvalidator = recommendationCacheInvalidator;
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
            profile.setMajor(normalizeText(request.getMajor(), 100, "专业名称"));
        }
        if (request.getSkills() != null) {
            profile.setSkills(normalizeSkills(request.getSkills()));
        }
        if (request.getEducation() != null) {
            profile.setEducation(normalizeEducation(request.getEducation()));
        }
        if (request.getPreferredCity() != null) {
            profile.setPreferredCity(normalizeCities(request.getPreferredCity()));
        }
        if (request.getSalaryMin() != null) {
            profile.setSalaryMin(request.getSalaryMin());
        }
        if (request.getSalaryMax() != null) {
            profile.setSalaryMax(request.getSalaryMax());
        }
        if (profile.getSalaryMin() != null && profile.getSalaryMax() != null
                && profile.getSalaryMin() > profile.getSalaryMax()) {
            throw new IllegalArgumentException("最低薪资不能高于最高薪资");
        }

        ProfileResponse response = ProfileResponse.from(profileRepository.save(profile));

        evictRecommendationAfterCommit(userId);

        return response;
    }

    private void evictRecommendationAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recommendationCacheInvalidator.evictUser(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recommendationCacheInvalidator.evictUser(userId);
            }
        });
    }

    private String normalizeText(String value, int maxLength, String field) {
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "最长" + maxLength + "字符");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private List<String> normalizeSkills(List<String> skills) {
        if (skills.size() > 30) {
            throw new IllegalArgumentException("技能最多30项");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String skill : skills) {
            if (skill == null || skill.trim().isEmpty()) {
                throw new IllegalArgumentException("技能名称不能为空");
            }
            String display = skill.trim();
            if (display.length() > 50) {
                throw new IllegalArgumentException("单项技能最长50字符");
            }
            values.putIfAbsent(display.toLowerCase(Locale.ROOT), display);
        }
        return new ArrayList<>(values.values());
    }

    private String normalizeEducation(String education) {
        String normalized = education.trim().replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.endsWith("及以上")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        if (!List.of("不限", "大专", "本科", "硕士", "博士").contains(normalized)) {
            throw new IllegalArgumentException("学历仅支持不限、大专、本科、硕士或博士");
        }
        return normalized;
    }

    private String normalizeCities(String preferredCity) {
        if (preferredCity.isBlank()) {
            return null;
        }
        String[] cities = preferredCity.split("[,，、\\s]+");
        if (cities.length > 10) {
            throw new IllegalArgumentException("意向城市最多10个");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String city : cities) {
            String display = city.trim();
            if (display.isEmpty()) {
                continue;
            }
            if (display.length() > 30) {
                throw new IllegalArgumentException("单个城市最长30字符");
            }
            values.putIfAbsent(display.toLowerCase(Locale.ROOT), display);
        }
        return String.join(",", values.values());
    }
}
