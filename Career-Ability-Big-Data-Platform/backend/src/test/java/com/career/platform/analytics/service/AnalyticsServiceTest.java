package com.career.platform.analytics.service;

import com.career.platform.position.entity.JobCompany;
import com.career.platform.analytics.dto.AnalyticsDimension;
import com.career.platform.analytics.dto.AnalyticsFilter;
import com.career.platform.analytics.dto.AnalyticsSnapshotRequest;
import com.career.platform.analytics.dto.AnalyticsSnapshotResponse;
import com.career.platform.common.security.PublicRecruitmentScopePolicy;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {
    private AnalyticsService analytics;
    private PositionRepository repository;
    private List<JobPosition> fixturePositions;

    @BeforeEach
    void setUp() {
        repository = mock(PositionRepository.class);
        fixturePositions = List.of(
                position("Java工程师", "上海", 10, 20, List.of("Java", "MySQL", "Java")),
                position("数据工程师", "杭州", 20, 30, List.of("Python", "MySQL")),
                position("薪资面议岗位", "上海", 0, 0, List.of())
        );
        when(repository.findAllWithCompany()).thenReturn(fixturePositions);
        when(repository.findAll(any(Specification.class))).thenReturn(fixturePositions);
        analytics = fixedAnalytics();
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
        assertThat(filter.cacheKey()).contains("上海").contains("java").contains("互联网");

        filter.setStartDate(LocalDate.now());
        filter.setEndDate(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> analytics.salaryFor(filter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startDate");
    }

    @Test
    void calculatesTheWholeDashboardWithOneDatabaseRead() {
        assertThat(analytics.calculateSnapshot()).containsKeys(
                "overview", "positions", "salary", "skills", "education", "city", "company", "trends");
        verify(repository).findAllWithCompany();
    }

    @Test
    void returnsStableZeroAndEmptyValuesForEveryDimensionWhenTheDatasetIsEmpty() {
        when(repository.findAllWithCompany()).thenReturn(List.of());

        Map<String, Object> snapshot = analytics.calculateSnapshot();

        @SuppressWarnings("unchecked")
        Map<String, Object> overview = (Map<String, Object>) snapshot.get("overview");
        @SuppressWarnings("unchecked")
        Map<String, Object> salary = (Map<String, Object>) snapshot.get("salary");
        @SuppressWarnings("unchecked")
        Map<String, Object> positions = (Map<String, Object>) snapshot.get("positions");
        @SuppressWarnings("unchecked")
        Map<String, Object> trends = (Map<String, Object>) snapshot.get("trends");
        assertThat(overview).containsEntry("totalPositions", 0).containsEntry("averageSalary", 0.0);
        assertThat(positions).containsEntry("total", 0).containsEntry("monthlyGrowthRate", 0.0);
        assertThat(salary).containsEntry("average", 0.0).containsEntry("median", 0.0);
        assertThat((List<?>) ((Map<?, ?>) snapshot.get("skills")).get("topSkills")).isEmpty();
        assertThat((List<?>) ((Map<?, ?>) snapshot.get("education")).get("distribution")).isEmpty();
        assertThat((List<?>) ((Map<?, ?>) snapshot.get("city")).get("ranking")).isEmpty();
        assertThat((List<?>) ((Map<?, ?>) snapshot.get("company")).get("industryDistribution")).isEmpty();
        assertThat(trends).containsEntry("monthOverMonth", 0.0).containsEntry("yearOverYear", 0.0);
    }

    @Test
    void producesAReproducibleSevenDimensionSnapshotFromTheFinalSample() throws IOException {
        List<JobPosition> sample = finalSample();
        when(repository.findAllWithCompany()).thenReturn(sample);
        when(repository.findAll(any(Specification.class))).thenReturn(sample);

        Map<String, Object> snapshot = analytics.calculateSnapshot();
        assertThat(snapshot).containsKeys("overview", "positions", "salary", "skills", "education", "city", "company", "trends");

        @SuppressWarnings("unchecked")
        Map<String, Object> overview = (Map<String, Object>) snapshot.get("overview");
        @SuppressWarnings("unchecked")
        Map<String, Object> positions = (Map<String, Object>) snapshot.get("positions");
        @SuppressWarnings("unchecked")
        Map<String, Object> salary = (Map<String, Object>) snapshot.get("salary");
        @SuppressWarnings("unchecked")
        Map<String, Object> skills = (Map<String, Object>) snapshot.get("skills");
        @SuppressWarnings("unchecked")
        Map<String, Object> education = (Map<String, Object>) snapshot.get("education");
        @SuppressWarnings("unchecked")
        Map<String, Object> city = (Map<String, Object>) snapshot.get("city");
        @SuppressWarnings("unchecked")
        Map<String, Object> company = (Map<String, Object>) snapshot.get("company");
        @SuppressWarnings("unchecked")
        Map<String, Object> trends = (Map<String, Object>) snapshot.get("trends");

        assertThat(overview).containsEntry("totalPositions", 520).containsEntry("averageSalary", 25.0)
                .containsEntry("activeCompanies", 40L);
        assertThat(positions).containsEntry("total", 520).containsEntry("monthlyGrowthRate", 3.1);
        assertThat(salary).containsEntry("average", 25.0).containsEntry("median", 22.0);
        assertThat(namedValue((List<Map<String, Object>>) skills.get("topSkills"), "Python")).isEqualTo(91L);
        assertThat(namedValue((List<Map<String, Object>>) education.get("distribution"), "本科")).isEqualTo(269L);
        assertThat(namedValue((List<Map<String, Object>>) city.get("ranking"), "大连")).isEqualTo(34L);
        assertThat(namedValue((List<Map<String, Object>>) company.get("industryDistribution"), "互联网/IT")).isEqualTo(69L);
        assertThat(trends).containsEntry("monthOverMonth", 3.1).containsEntry("yearOverYear", 100.0);
    }

    @Test
    void snapshotHonorsDateAndDimensionContractAndReturnsItsActualBoundary() {
        JobPosition june = position("Java工程师", "上海", 10, 20, List.of("Java"));
        june.setPublishDate(LocalDate.of(2026, 6, 10));
        JobPosition july = position("数据工程师", "杭州", 20, 30, List.of("Python"));
        july.setPublishDate(LocalDate.of(2026, 7, 10));
        when(repository.findAllWithCompany()).thenReturn(List.of(june, july));
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(june));

        AnalyticsSnapshotRequest request = new AnalyticsSnapshotRequest();
        request.setStartDate(LocalDate.of(2026, 6, 1));
        request.setEndDate(LocalDate.of(2026, 6, 30));
        request.setDimensions(EnumSet.of(AnalyticsDimension.SALARY, AnalyticsDimension.TREND));

        AnalyticsSnapshotResponse result = analytics.snapshot(request);

        assertThat(result.getScope().value()).isEqualTo("PUBLIC_RECRUITMENT");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(result.getDimensions()).containsExactlyInAnyOrder(AnalyticsDimension.SALARY, AnalyticsDimension.TREND);
        assertThat(result.getData()).containsOnlyKeys("overview", "salary", "trends");
        assertThat((Map<String, Object>) result.getData().get("overview")).containsEntry("totalPositions", 1);
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void anchorsAndCropsHistoricalSnapshotTrendsToRequestedRange() {
        JobPosition first = position("Java工程师", "上海", 10, 20, List.of("Java"));
        first.setPublishDate(LocalDate.of(2026, 6, 10));
        JobPosition last = position("数据工程师", "杭州", 20, 30, List.of("Python"));
        last.setPublishDate(LocalDate.of(2026, 6, 20));
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(first, last));

        AnalyticsSnapshotRequest request = new AnalyticsSnapshotRequest();
        request.setStartDate(LocalDate.of(2026, 6, 10));
        request.setEndDate(LocalDate.of(2026, 6, 20));
        request.setDimensions(EnumSet.of(AnalyticsDimension.TREND));

        AnalyticsSnapshotResponse result = analytics.snapshot(request);
        Map<String, Object> trends = (Map<String, Object>) result.getData().get("trends");
        List<Map<String, Object>> daily = (List<Map<String, Object>>) trends.get("daily");
        List<Map<String, Object>> monthly = (List<Map<String, Object>>) trends.get("monthly");

        assertThat(daily).hasSize(11);
        assertThat(daily.get(0)).containsEntry("name", "2026-06-10").containsEntry("value", 1L);
        assertThat(daily.get(5)).containsEntry("name", "2026-06-15").containsEntry("value", 0L);
        assertThat(daily.get(10)).containsEntry("name", "2026-06-20").containsEntry("value", 1L);
        assertThat(monthly).containsExactly(Map.of("name", "2026-06", "value", 2L));
        assertThat(trends).containsEntry("monthOverMonth", 100.0).containsEntry("yearOverYear", 100.0);
    }

    private AnalyticsService fixedAnalytics() {
        return new AnalyticsService(repository, new PublicRecruitmentScopePolicy(),
                Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    private long namedValue(List<Map<String, Object>> values, String name) {
        return ((Number) values.stream()
                .filter(value -> name.equals(value.get("name")))
                .findFirst()
                .orElseThrow()
                .get("value")).longValue();
    }

    private List<JobPosition> finalSample() throws IOException {
        Path path = Path.of("..", "data", "kaggle_jobs_500.csv");
        if (!Files.exists(path)) {
            path = Path.of("data", "kaggle_jobs_500.csv");
        }
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .skip(1)
                .map(this::samplePosition)
                .toList();
    }

    private JobPosition samplePosition(String line) {
        String[] values = line.split(",", -1);
        JobCompany company = new JobCompany();
        company.setCompanyName(values[2]);
        company.setCompanySize(values[3]);
        company.setIndustry(values[4]);
        company.setCompanyType(values[5]);
        JobPosition position = new JobPosition();
        position.setJobId(values[0]);
        position.setTitle(values[1]);
        position.setCompany(company);
        position.setSalaryMin(Integer.valueOf(values[6]));
        position.setSalaryMax(Integer.valueOf(values[7]));
        position.setCity(values[8]);
        position.setProvince(values[9]);
        position.setEducation(values[11]);
        position.setExperience(values[12]);
        position.setSkills(List.of(values[13].split("\\|")));
        position.setPublishDate(LocalDate.parse(values[15]));
        return position;
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
