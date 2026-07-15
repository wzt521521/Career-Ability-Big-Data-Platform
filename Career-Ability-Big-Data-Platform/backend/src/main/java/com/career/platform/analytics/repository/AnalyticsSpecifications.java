package com.career.platform.analytics.repository;

import com.career.platform.analytics.dto.AnalyticsFilter;
import com.career.platform.position.entity.JobCompany;
import com.career.platform.position.entity.JobPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/** Database predicates used by date- and dimension-scoped analytics snapshots. */
public final class AnalyticsSpecifications {
    private AnalyticsSpecifications() { }

    public static Specification<JobPosition> from(AnalyticsFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<JobPosition, JobCompany> company = root.join("company", JoinType.LEFT);
            if (filter.getStartDate() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("publishDate"), filter.getStartDate()));
            }
            if (filter.getEndDate() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("publishDate"), filter.getEndDate()));
            }
            if (hasText(filter.getCity())) {
                predicates.add(builder.equal(root.get("city"), filter.getCity().trim()));
            }
            if (hasText(filter.getPosition())) {
                predicates.add(builder.like(builder.lower(root.get("title")), "%"
                        + filter.getPosition().trim().toLowerCase(Locale.ROOT) + "%"));
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
