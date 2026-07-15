package com.career.platform.profile.service.impl;

import com.career.platform.profile.dto.ProfileRequest;
import com.career.platform.profile.dto.ProfileResponse;
import com.career.platform.profile.entity.StudentProfile;
import com.career.platform.profile.repository.ProfileRepository;
import com.career.platform.recommend.service.RecommendationCacheInvalidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceImplTest {

    private ProfileRepository profileRepository;
    private RecommendationCacheInvalidator cacheInvalidator;
    private ProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        profileRepository = mock(ProfileRepository.class);
        cacheInvalidator = mock(RecommendationCacheInvalidator.class);
        service = new ProfileServiceImpl(profileRepository, cacheInvalidator);
    }

    @Test
    void returnsProfilesByTheRequestedUserIdOnly() {
        StudentProfile first = profile(11L, "first-major");
        StudentProfile second = profile(22L, "second-major");
        when(profileRepository.findByUserId(11L)).thenReturn(Optional.of(first));
        when(profileRepository.findByUserId(22L)).thenReturn(Optional.of(second));

        ProfileResponse firstResponse = service.getProfile(11L);
        ProfileResponse secondResponse = service.getProfile(22L);

        assertEquals("first-major", firstResponse.getMajor());
        assertEquals("second-major", secondResponse.getMajor());
        verify(profileRepository).findByUserId(11L);
        verify(profileRepository).findByUserId(22L);
    }

    @Test
    void updatesOneProfileAndInvalidatesOnlyThatUsersRecommendationCache() {
        StudentProfile first = profile(11L, "old-major");

        when(profileRepository.findByUserId(11L)).thenReturn(Optional.of(first));
        when(profileRepository.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileRequest request = new ProfileRequest();
        request.setMajor("updated-major");
        request.setSkills(List.of("java", "spring"));
        ProfileResponse response = service.saveOrUpdate(11L, request);

        assertEquals("updated-major", response.getMajor());
        assertEquals(11L, first.getUserId());
        verify(profileRepository).findByUserId(11L);
        verify(profileRepository, never()).findByUserId(22L);
        verify(profileRepository).save(first);
        verify(cacheInvalidator).evictUser(11L);
    }

    @Test
    void normalizesProfileInputsWithoutLowercasingDisplaySkillNames() {
        StudentProfile profile = profile(11L, "old-major");
        when(profileRepository.findByUserId(11L)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProfileRequest request = new ProfileRequest();
        request.setSkills(List.of(" Java ", "java", "Spring Boot"));
        request.setPreferredCity(" Shanghai, Beijing,Shanghai ");
        request.setEducation("本科及以上");

        service.saveOrUpdate(11L, request);

        assertEquals(List.of("Java", "Spring Boot"), profile.getSkills());
        assertEquals("Shanghai,Beijing", profile.getPreferredCity());
        assertEquals("本科", profile.getEducation());
    }

    @Test
    void rejectsSkillsAboveTheBoundedProfileContract() {
        ProfileRequest request = new ProfileRequest();
        request.setSkills(java.util.Collections.nCopies(31, "Java"));
        when(profileRepository.findByUserId(11L)).thenReturn(Optional.of(profile(11L, "major")));

        assertThrows(IllegalArgumentException.class, () -> service.saveOrUpdate(11L, request));
    }

    private StudentProfile profile(Long userId, String major) {
        StudentProfile profile = new StudentProfile();
        profile.setUserId(userId);
        profile.setMajor(major);
        profile.setSkills(List.of("java"));
        return profile;
    }
}
