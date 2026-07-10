package com.career.platform.position.repository;

import com.career.platform.position.dto.PositionFilter;
import com.career.platform.position.entity.JobCompany;
import com.career.platform.position.entity.JobPosition;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PositionSpecifications {
    private PositionSpecifications() { }

    public static Specification<JobPosition> from(PositionFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<JobPosition, JobCompany> company = root.join("company", JoinType.LEFT);

            if (hasText(filter.getKeyword())) {
                String value = "%" + filter.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), value),
                        builder.like(builder.lower(company.get("companyName")), value)));
            }
            if (hasText(filter.getCity())) {
                predicates.add(builder.equal(root.get("city"), filter.getCity().trim()));
            }
            if (filter.getSalaryMin() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("salaryMax"), filter.getSalaryMin()));
            }
            if (filter.getSalaryMax() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("salaryMin"), filter.getSalaryMax()));
            }
            if (hasText(filter.getEducation())) {
                predicates.add(builder.equal(root.get("education"), filter.getEducation().trim()));
            }
            if (hasText(filter.getExperience())) {
                predicates.add(builder.equal(root.get("experience"), filter.getExperience().trim()));
            }
            if (hasText(filter.getIndustry())) {
                predicates.add(builder.equal(company.get("industry"), filter.getIndustry().trim()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
