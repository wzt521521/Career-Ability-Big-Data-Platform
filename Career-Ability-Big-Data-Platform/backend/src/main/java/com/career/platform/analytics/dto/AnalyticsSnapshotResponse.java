package com.career.platform.analytics.dto;

import com.career.platform.common.security.PublicRecruitmentScope;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable result consumed by reporting and Open API adapters. */
public final class AnalyticsSnapshotResponse {
    private final PublicRecruitmentScope scope;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Set<AnalyticsDimension> dimensions;
    private final Map<String, Object> data;
    private final boolean empty;
    private final LocalDateTime generatedAt;

    public AnalyticsSnapshotResponse(PublicRecruitmentScope scope,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     Set<AnalyticsDimension> dimensions,
                                     Map<String, Object> data,
                                     boolean empty,
                                     LocalDateTime generatedAt) {
        this.scope = scope;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dimensions = Set.copyOf(dimensions);
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.empty = empty;
        this.generatedAt = generatedAt;
    }

    public PublicRecruitmentScope getScope() { return scope; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Set<AnalyticsDimension> getDimensions() { return dimensions; }
    public Map<String, Object> getData() { return data; }
    public boolean isEmpty() { return empty; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
}
