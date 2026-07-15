package com.career.platform.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.career.platform.analytics.dto.AnalyticsDimension;
import com.career.platform.analytics.dto.AnalyticsSnapshotRequest;
import com.career.platform.analytics.dto.AnalyticsSnapshotResponse;
import com.career.platform.common.security.PublicRecruitmentScopePolicy;
import com.career.platform.position.entity.JobCompany;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.CompanyRepository;
import com.career.platform.position.repository.PositionRepository;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({AnalyticsService.class, PublicRecruitmentScopePolicy.class})
@ActiveProfiles("test")
class AnalyticsSnapshotIntegrationTest {

    @Autowired private AnalyticsService analytics;
    @Autowired private PositionRepository positions;
    @Autowired private CompanyRepository companies;

    @BeforeEach
    void seed() {
        positions.deleteAll();
        companies.deleteAll();
        JobCompany company = new JobCompany();
        company.setCompanyName("范围测试企业");
        company.setIndustry("互联网");
        company = companies.save(company);
        positions.saveAll(List.of(
                position("scope-june", company, "上海", LocalDate.of(2026, 6, 15)),
                position("scope-july", company, "杭州", LocalDate.of(2026, 7, 15))));
    }

    @Test
    void queriesOnlyTheRequestedInclusiveDateRangeAtTheRepositoryBoundary() {
        AnalyticsSnapshotRequest request = new AnalyticsSnapshotRequest();
        request.setStartDate(LocalDate.of(2026, 6, 1));
        request.setEndDate(LocalDate.of(2026, 6, 30));
        request.setDimensions(EnumSet.of(AnalyticsDimension.SALARY, AnalyticsDimension.CITY));

        AnalyticsSnapshotResponse snapshot = analytics.snapshot(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> overview = (Map<String, Object>) snapshot.getData().get("overview");
        assertThat(overview).containsEntry("totalPositions", 1);
        assertThat(snapshot.getData()).containsOnlyKeys("overview", "salary", "city");
        assertThat(snapshot.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(snapshot.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    private JobPosition position(String jobId, JobCompany company, String city, LocalDate publishDate) {
        JobPosition position = new JobPosition();
        position.setJobId(jobId);
        position.setTitle("Java工程师");
        position.setCompany(company);
        position.setSalaryMin(10);
        position.setSalaryMax(20);
        position.setCity(city);
        position.setProvince(city);
        position.setEducation("本科");
        position.setExperience("3-5年");
        position.setSkills(List.of("Java"));
        position.setPublishDate(publishDate);
        return position;
    }
}
