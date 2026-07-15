package com.career.platform.position.service;

import com.career.platform.common.ResourceNotFoundException;
import com.career.platform.common.security.PublicRecruitmentScopePolicy;
import com.career.platform.position.dto.PositionFilter;
import com.career.platform.position.entity.JobCompany;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.CompanyRepository;
import com.career.platform.position.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({PositionService.class, PublicRecruitmentScopePolicy.class})
@ActiveProfiles("test")
class PositionServiceIntegrationTest {
    @Autowired PositionService service;
    @Autowired PositionRepository positions;
    @Autowired CompanyRepository companies;

    @BeforeEach
    void seed() {
        JobCompany alpha = company("星河科技", "互联网");
        JobCompany beta = company("远山数据", "人工智能");
        positions.save(position("job-1", "Java开发工程师", alpha, 10, 18, "上海", "本科"));
        positions.save(position("job-2", "数据分析师", beta, 16, 25, "杭州", "硕士"));
        positions.save(position("job-3", "前端工程师", alpha, 8, 14, "上海", "本科"));
    }

    @Test
    void searchesTitleAndCompanyWithOneBasedPagination() {
        PositionFilter titleFilter = new PositionFilter();
        titleFilter.setKeyword("Java");
        titleFilter.setSize(1);
        assertThat(service.search(titleFilter).getContent()).extracting("title")
                .containsExactly("Java开发工程师");
        assertThat(service.search(titleFilter).getNumber()).isEqualTo(1);

        PositionFilter companyFilter = new PositionFilter();
        companyFilter.setKeyword("星河");
        assertThat(service.search(companyFilter).getTotalElements()).isEqualTo(2);
    }

    @Test
    void appliesSalaryOverlapAndStructuredFilters() {
        PositionFilter filter = new PositionFilter();
        filter.setSalaryMin(15);
        filter.setSalaryMax(20);
        assertThat(service.search(filter).getContent()).extracting("title")
                .containsExactlyInAnyOrder("Java开发工程师", "数据分析师");

        filter.setCity("杭州");
        filter.setEducation("硕士");
        filter.setIndustry("人工智能");
        assertThat(service.search(filter).getContent()).extracting("title")
                .containsExactly("数据分析师");
    }

    @Test
    void returnsDetailAndValidatesBoundaries() {
        Long id = positions.findAll().get(0).getId();
        assertThat(service.get(id).getSourceUrl()).isEqualTo("https://example.test/job");
        assertThatThrownBy(() -> service.get(9999L)).isInstanceOf(ResourceNotFoundException.class);

        PositionFilter invalid = new PositionFilter();
        invalid.setPage(0);
        assertThatThrownBy(() -> service.search(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.latest(101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suppliesSuggestionsAndPreservesTheUserSelectedComparisonOrder() {
        List<JobPosition> all = positions.findAll();
        Long second = all.get(1).getId();
        Long first = all.get(0).getId();

        assertThat(service.suggestPublicTitles("工程", 10))
                .contains("Java开发工程师", "前端工程师");
        assertThat(service.comparePublicRecruitment(List.of(second, first)))
                .extracting("id")
                .containsExactly(second, first);
        assertThatThrownBy(() -> service.comparePublicRecruitment(List.of(first)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JobCompany company(String name, String industry) {
        JobCompany company = new JobCompany();
        company.setCompanyName(name);
        company.setCompanySize("150-500人");
        company.setIndustry(industry);
        company.setCompanyType("民营");
        return companies.save(company);
    }

    private JobPosition position(String jobId, String title, JobCompany company, int min, int max,
                                 String city, String education) {
        JobPosition position = new JobPosition();
        position.setJobId(jobId);
        position.setTitle(title);
        position.setCompany(company);
        position.setSalaryMin(min);
        position.setSalaryMax(max);
        position.setCity(city);
        position.setProvince(city);
        position.setEducation(education);
        position.setExperience("1-3年");
        position.setSkills(List.of("Java", "MySQL"));
        position.setWelfare(List.of("五险一金"));
        position.setPublishDate(LocalDate.now());
        position.setSourceUrl("https://example.test/job");
        return position;
    }
}
