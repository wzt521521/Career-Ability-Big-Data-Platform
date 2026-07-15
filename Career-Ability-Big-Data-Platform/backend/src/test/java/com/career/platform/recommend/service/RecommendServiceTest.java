package com.career.platform.recommend.service;

import com.career.platform.common.error.BusinessException;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.PositionRepository;
import com.career.platform.profile.entity.StudentProfile;
import com.career.platform.profile.repository.ProfileRepository;
import com.career.platform.recommend.dto.RecommendationResponse;
import com.career.platform.recommend.dto.RecommendationCacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecommendServiceTest {

    private RecommendService service;
    private ProfileRepository profileRepository;
    private PositionRepository positionRepository;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        profileRepository = mock(ProfileRepository.class);
        positionRepository = mock(PositionRepository.class);
        cacheManager = cacheManager();
        service = new RecommendService(
                profileRepository,
                positionRepository,
                cacheManager);
    }

    @Test
    void calculatesJaccardScoreFromSkillIntersection() {
        double score = service.calcSkillScore(
                List.of("java", "spring"),
                List.of("java", "mysql"));

        assertEquals(1.0 / 3.0, score, 0.000001);
    }

    @Test
    void returnsZeroWhenEitherSkillSetIsEmpty() {
        assertEquals(0.0, service.calcSkillScore(List.of(), List.of("java")));
        assertEquals(0.0, service.calcSkillScore(List.of("java"), List.of()));
    }

    @Test
    void normalizesCaseWhitespaceNullsAndDuplicates() {
        List<String> normalized = service.normalizeSkills(
                Arrays.asList(" Java ", "JAVA", null, "", " Spring "));

        assertEquals(List.of("java", "spring"), normalized);
    }

    @Test
    void calculatesCityEducationSalaryAndMajorScores() {
        assertEquals(1.0, service.calcCityScore(List.of("Shanghai"), "Shanghai City"));
        assertEquals(0.3, service.calcCityScore(List.of("Shanghai"), "Beijing"));

        assertEquals(0.4, service.calcEducationScore("\u672c\u79d1", "\u7855\u58eb"));
        assertEquals(0.8, service.calcEducationScore("\u7855\u58eb", "\u672c\u79d1"));
        assertEquals(1.0, service.calcEducationScore("\u5927\u4e13", "\u4e0d\u9650"));

        assertEquals(0.5, service.calcSalaryScore(10, 20, 15, 25));
        assertEquals(0.0, service.calcSalaryScore(10, 20, 20, 30));

        assertEquals(0.5, service.calcMajorScore("abc xy", "abc zz"));
        assertEquals(0.0, service.calcMajorScore("abc xy", "def zz"));
    }

    @Test
    void cachesAndLimitsRecommendationsToTopTwenty() {
        StudentProfile profile = profile(1L);
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(positionRepository.findLatest(any())).thenReturn(
                IntStream.rangeClosed(1, 25)
                        .mapToObj(this::position)
                        .collect(java.util.stream.Collectors.toList()));

        List<?> firstPage = service.recommend(1L, 1, 20);

        assertEquals(20, firstPage.size());
        assertEquals(20, service.count(1L));
        assertTrue(service.recommend(1L, 2, 20).isEmpty());

        verify(positionRepository, times(1)).findLatest(any());
        verify(positionRepository, never()).count();
    }

    @Test
    void enforcesTopTwentyForAStaleCachedRecommendationList() {
        List<RecommendationResponse> staleResults = IntStream.range(0, 25)
                .mapToObj(ignored -> mock(RecommendationResponse.class))
                .collect(java.util.stream.Collectors.toList());
        cacheManager.getCache("recommend").put("local:1", new RecommendationCacheEntry(staleResults));

        assertEquals(20, service.recommend(1L, 1, 20).size());
        assertEquals(20, service.count(1L));
        verifyNoInteractions(profileRepository, positionRepository);
    }

    @Test
    void rejectsRecommendationPaginationOutsideTheTopTwentyContract() {
        assertThrows(BusinessException.class, () -> service.recommend(1L, 0, 20));
        assertThrows(BusinessException.class, () -> service.recommend(1L, 1, 0));
        assertThrows(BusinessException.class, () -> service.recommend(1L, 1, 21));
    }

    private CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("recommend");
    }

    private StudentProfile profile(Long userId) {
        StudentProfile profile = new StudentProfile();
        profile.setUserId(userId);
        profile.setSkills(List.of("java"));
        profile.setPreferredCity("Shanghai");
        profile.setEducation("\u672c\u79d1");
        profile.setSalaryMin(10);
        profile.setSalaryMax(20);
        profile.setMajor("computer science");
        return profile;
    }

    private JobPosition position(int number) {
        JobPosition position = new JobPosition();
        position.setJobId("position-" + number);
        position.setTitle("computer engineer " + number);
        position.setSkills(List.of("java"));
        position.setCity("Shanghai");
        position.setEducation("\u672c\u79d1");
        position.setSalaryMin(10);
        position.setSalaryMax(20);
        return position;
    }
}
