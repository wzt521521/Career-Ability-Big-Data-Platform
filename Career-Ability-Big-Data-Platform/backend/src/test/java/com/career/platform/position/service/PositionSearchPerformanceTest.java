package com.career.platform.position.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.career.platform.common.security.PublicRecruitmentScopePolicy;
import com.career.platform.position.dto.PositionFilter;
import com.career.platform.position.entity.JobCompany;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.CompanyRepository;
import com.career.platform.position.repository.PositionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** C-05 local regression: representative filtered paging remains practical at ten-thousand-row scale. */
@DataJpaTest
@Import({PositionService.class, PublicRecruitmentScopePolicy.class})
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "includePerformanceTests", matches = "true")
class PositionSearchPerformanceTest {

    @Autowired private PositionService service;
    @Autowired private PositionRepository positions;
    @Autowired private CompanyRepository companies;
    @Autowired private EntityManager entityManager;

    @Test
    void keepsFilteredPagingP95BelowTwoSecondsForTenThousandRows() {
        seedTenThousandPositions();
        PositionFilter filter = new PositionFilter();
        filter.setCity("上海");
        filter.setSortBy("publishDate");
        filter.setSize(20);

        // Warm up the JPA query plan and the database page cache before measuring P95.
        service.searchPublicRecruitment(filter);
        List<Long> durations = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            long started = System.nanoTime();
            service.searchPublicRecruitment(filter);
            durations.add(System.nanoTime() - started);
        }
        Collections.sort(durations);
        long p95Millis = durations.get((int) Math.ceil(durations.size() * 0.95) - 1) / 1_000_000;

        assertThat(p95Millis).isLessThan(2_000L);
    }

    private void seedTenThousandPositions() {
        positions.deleteAll();
        companies.deleteAll();
        JobCompany company = new JobCompany();
        company.setCompanyName("性能测试企业");
        company.setIndustry("互联网");
        company = companies.saveAndFlush(company);

        List<JobPosition> batch = new ArrayList<>(10_000);
        for (int index = 0; index < 10_000; index++) {
            JobPosition position = new JobPosition();
            position.setJobId("performance-" + index);
            position.setTitle(index % 2 == 0 ? "Java工程师" : "数据工程师");
            position.setCompany(company);
            position.setSalaryMin(10 + index % 20);
            position.setSalaryMax(20 + index % 20);
            position.setCity(index % 4 == 0 ? "上海" : "杭州");
            position.setProvince(position.getCity());
            position.setEducation(index % 3 == 0 ? "本科" : "硕士");
            position.setExperience("3-5年");
            position.setSkills(List.of("Java", "SQL"));
            position.setPublishDate(LocalDate.of(2026, 1, 1).plusDays(index % 180));
            batch.add(position);
        }
        positions.saveAll(batch);
        positions.flush();
        entityManager.clear();
    }
}
