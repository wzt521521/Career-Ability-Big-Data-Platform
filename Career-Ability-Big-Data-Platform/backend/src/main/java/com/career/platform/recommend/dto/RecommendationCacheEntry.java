package com.career.platform.recommend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Versioned, bounded cache payload with an explicit JSON shape for Redis. */
public final class RecommendationCacheEntry {

    private final List<RecommendationResponse> recommendations;

    @JsonCreator
    public RecommendationCacheEntry(@JsonProperty("recommendations") List<RecommendationResponse> recommendations) {
        this.recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }

    public List<RecommendationResponse> getRecommendations() {
        return recommendations;
    }
}
