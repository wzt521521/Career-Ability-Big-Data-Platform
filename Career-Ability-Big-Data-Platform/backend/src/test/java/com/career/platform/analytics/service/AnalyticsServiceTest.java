package com.career.platform.analytics.service;

import com.career.platform.position.entity.JobCompany;
import com.career.platform.analytics.dto.AnalyticsFilter;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {
    private AnalyticsService analytics;

    @BeforeEach
    void setUp() {
        PositionRepository repository = mock(PositionRepository.class);
        when(repository.findAllWithCompany()).thenReturn(List.of(
                position("Java工程师", "上海", 10, 20, List.of("Java", "MySQL", "Java")),
                position("数据工程师", "杭州", 20, 30, List.of("Python", "MySQL")),
                position("薪资面议岗位", "上海", 0, 0, List.of())
        ));
        analytics = new AnalyticsService(repository);
    }

    @Test
    void calculatesSalaryWithoutCountingUnknownValues() {
        Map<String, Object> salary = analytics.salary();
        assertThat(salary.get("average")).isEqualTo(20.0);
        assertThat(salary.get("median")).isEqualTo(20.0);
        assertThat((List<?>) salary.get("distribution")).hasSize(6);
    }

    @Test
    void deduplicatesSkillsWithinOnePositionAndBuildsAssociations() {
        Map<String, Object> skills = analytics.skills();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) skills.get("topSkills");
        Map<String, Object> mysql = top.stream().filter(item -> item.get("name").equals("MySQL")).findFirst().orElseThrow();
        Map<String, Object> java = top.stream().filter(item -> item.get("name").equals("Java")).findFirst().orElseThrow();
        assertThat(mysql.get("value")).isEqualTo(2L);
        assertThat(java.get("value")).isEqualTo(1L);
        assertThat((List<?>) skills.get("associations")).isNotEmpty();
    }

    @Test
    void emitsCompleteOverviewAndTwelveMonthTrend() {
        assertThat(analytics.overview()).containsEntry("totalPositions", 3).containsEntry("activeCompanies", 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> monthly = (List<Map<String, Object>>) analytics.trends().get("monthly");
        assertThat(monthly).hasSize(12);
    }

    @Test
    void supportsCrossDimensionFiltersAndRejectsInvalidDates() {
        AnalyticsFilter filter = new AnalyticsFilter();
        filter.setCity("上海");
        filter.setPosition("Java");
        filter.setIndustry("互联网");
        assertThat(analytics.overviewFor(filter)).containsEntry("totalPositions", 1);

        filter.setStartDate(LocalDate.now());
        filter.setEndDate(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> analytics.salaryFor(filter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startDate");
    }

    private JobPosition position(String title, String city, int min, int max, List<String> skills) {
        JobCompany company = new JobCompany();
        company.setCompanyName("测试公司");
        company.setIndustry("互联网");
        company.setCompanySize("150-500人");
        JobPosition position = new JobPosition();
        position.setTitle(title);
        position.setCompany(company);
        position.setCity(city);
        position.setProvince(city);
        position.setEducation("本科");
        position.setSalaryMin(min);
        position.setSalaryMax(max);
        position.setSkills(skills);
        position.setPublishDate(LocalDate.now());
        return position;
    }
}
