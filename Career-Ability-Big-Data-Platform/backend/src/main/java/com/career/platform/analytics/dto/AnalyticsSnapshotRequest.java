package com.career.platform.analytics.dto;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * A reproducible analytics query. A missing date boundary means the full public-recruitment
 * history; supplied boundaries are inclusive and are never replaced by a display-only label.
 */
public class AnalyticsSnapshotRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private String city;
    private String position;
    private String industry;
    private Set<AnalyticsDimension> dimensions = EnumSet.allOf(AnalyticsDimension.class);

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public Set<AnalyticsDimension> getDimensions() {
        return dimensions == null || dimensions.isEmpty()
                ? EnumSet.allOf(AnalyticsDimension.class)
                : EnumSet.copyOf(dimensions);
    }

    public void setDimensions(Set<AnalyticsDimension> dimensions) {
        this.dimensions = dimensions == null || dimensions.isEmpty()
                ? EnumSet.allOf(AnalyticsDimension.class)
                : EnumSet.copyOf(dimensions);
    }

    public AnalyticsFilter toFilter() {
        AnalyticsFilter filter = new AnalyticsFilter();
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);
        filter.setCity(city);
        filter.setPosition(position);
        filter.setIndustry(industry);
        return filter;
    }
}
