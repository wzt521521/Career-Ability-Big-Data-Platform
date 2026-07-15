package com.career.platform.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.career.platform.recommend.service.RecommendationCacheInvalidator;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.jdbc.core.JdbcTemplate;

class OfflineAnalysisServiceTest {

    @Test
    void persistsEveryDailyStatisticTableAndInvalidatesAllAnalyticsCaches() throws Exception {
        AnalyticsService analytics = mock(AnalyticsService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RecommendationCacheInvalidator recommendationCacheInvalidator = mock(RecommendationCacheInvalidator.class);
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        Map<String, Object> snapshot = snapshot();
        when(analytics.calculateSnapshot()).thenReturn(snapshot);
        OfflineAnalysisService service = new OfflineAnalysisService(
                analytics, jdbc, new ObjectMapper(), recommendationCacheInvalidator, cacheManager);

        service.calculateAndPersist();

        verify(analytics).calculateSnapshot();
        verify(recommendationCacheInvalidator).evictAll();
        verify(cacheManager, times(9)).getCache(anyString());
        verify(cache, times(9)).put(org.mockito.ArgumentMatchers.eq(SimpleKey.EMPTY), any());
        List<String> statements = mockingDetails(jdbc).getInvocations().stream()
                .filter(invocation -> "update".equals(invocation.getMethod().getName()))
                .map(invocation -> {
                    Object sql = invocation.getArgument(0);
                    return sql == null ? "" : sql.toString();
                })
                .collect(java.util.stream.Collectors.toList());
        assertThat(statements).hasSizeGreaterThanOrEqualTo(14);
        String allSql = String.join("\n", statements);
        for (String table : List.of("stat_position", "stat_salary", "stat_education", "stat_skill",
                "stat_city", "stat_company", "stat_trend")) {
            assertThat(allSql).contains("DELETE FROM " + table).contains("INSERT INTO " + table);
        }

        Method method = OfflineAnalysisService.class.getMethod("calculateAndPersist");
        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
        assertThat(cacheEvict).isNotNull();
        assertThat(cacheEvict.allEntries()).isTrue();
        assertThat(cacheEvict.beforeInvocation()).isTrue();
        assertThat(cacheEvict.cacheNames()).contains(
                "stat-overview", "stat-position", "stat-salary", "stat-skills", "stat-education",
                "stat-city", "stat-company", "stat-trends", "stat-dashboard");
    }

    private Map<String, Object> snapshot() {
        return Map.of(
                "overview", Map.of("totalPositions", 3, "newThisMonth", 1),
                "positions", Map.of("hotPositions", List.of(Map.of("name", "Java工程师", "value", 2)), "monthlyGrowthRate", 10.0),
                "salary", Map.of("average", 18.0, "median", 17.0,
                        "distribution", List.of(Map.of("name", "10-15K", "value", 1)),
                        "topPositions", List.of(Map.of("name", "Java工程师", "salary", 20.0))),
                "education", Map.of("distribution", List.of(Map.of("name", "本科", "value", 3)),
                        "salaryComparison", List.of(Map.of("name", "本科", "averageSalary", 18.0))),
                "skills", Map.of("topSkills", List.of(Map.of("name", "Java", "value", 2))),
                "city", Map.of("ranking", List.of(Map.of("name", "上海", "province", "上海", "value", 2)),
                        "salaryComparison", List.of(Map.of("name", "上海", "averageSalary", 18.0))),
                "company", Map.of("industryDistribution", List.of(Map.of("name", "互联网", "value", 3)),
                        "industryCompanyCounts", List.of(Map.of("name", "互联网", "value", 2)),
                        "activeCompanies", List.of(Map.of("name", "测试企业", "value", 2))),
                "trends", Map.of("daily", List.of(Map.of("name", "2026-07-14", "value", 1)),
                        "monthOverMonth", 10.0, "yearOverYear", 100.0));
    }
}
