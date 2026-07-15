package com.career.platform.report.service;

import com.career.platform.analytics.dto.AnalyticsDimension;
import com.career.platform.analytics.dto.AnalyticsSnapshotRequest;
import com.career.platform.analytics.dto.AnalyticsSnapshotResponse;
import com.career.platform.report.entity.ReportRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts report records to the C-module snapshot contract and stable template data. */
@Component
public class ReportSnapshotMapper {

    private final ObjectMapper objectMapper;

    public ReportSnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serializeDimensions(List<String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "";
        }
        return String.join(",", dimensions.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList());
    }

    public AnalyticsSnapshotRequest toSnapshotRequest(ReportRecord record) {
        AnalyticsSnapshotRequest request = new AnalyticsSnapshotRequest();
        request.setStartDate(record.getTimeRangeStart());
        request.setEndDate(record.getTimeRangeEnd());
        request.setCity(record.getFilterCity());
        request.setPosition(record.getFilterPosition());
        request.setIndustry(record.getFilterIndustry());
        request.setDimensions(resolveDimensions(record.getAnalysisDimensions()));
        return request;
    }

    public Map<String, Object> toTemplateModel(AnalyticsSnapshotResponse snapshot, ReportRecord record) {
        Map<String, Object> model = new LinkedHashMap<>(snapshot.getData());
        for (String key : List.of("positions", "salary", "skills", "education", "city", "company", "trends")) {
            model.putIfAbsent(key, Map.of());
        }
        model.put("reportTitle", record.getReportTitle());
        model.put("generateTime", snapshot.getGeneratedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.put("timeRangeStart", snapshot.getStartDate() == null ? "全部历史数据" : snapshot.getStartDate().toString());
        model.put("timeRangeEnd", snapshot.getEndDate() == null ? "当前" : snapshot.getEndDate().toString());
        model.put("emptyData", snapshot.isEmpty());
        model.put("analysisScope", serializeScope(snapshot, record));
        return model;
    }

    public String serializeScope(AnalyticsSnapshotResponse snapshot, ReportRecord record) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("scope", snapshot.getScope().value());
        scope.put("startDate", snapshot.getStartDate());
        scope.put("endDate", snapshot.getEndDate());
        scope.put("city", record.getFilterCity());
        scope.put("position", record.getFilterPosition());
        scope.put("industry", record.getFilterIndustry());
        scope.put("dimensions", snapshot.getDimensions().stream().map(Enum::name).sorted().toList());
        scope.put("empty", snapshot.isEmpty());
        scope.put("generatedAt", snapshot.getGeneratedAt());
        try {
            return objectMapper.writeValueAsString(scope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化报告查询范围", exception);
        }
    }

    private Set<AnalyticsDimension> resolveDimensions(String storedDimensions) {
        if (storedDimensions == null || storedDimensions.isBlank()) {
            return EnumSet.allOf(AnalyticsDimension.class);
        }
        Set<AnalyticsDimension> result = EnumSet.noneOf(AnalyticsDimension.class);
        for (String value : storedDimensions.split(",")) {
            switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "position", "positions" -> result.add(AnalyticsDimension.POSITION);
                case "salary" -> result.add(AnalyticsDimension.SALARY);
                case "education" -> result.add(AnalyticsDimension.EDUCATION);
                case "skill", "skills" -> result.add(AnalyticsDimension.SKILL);
                case "city" -> result.add(AnalyticsDimension.CITY);
                case "company", "industry" -> result.add(AnalyticsDimension.COMPANY);
                case "trend", "trends" -> result.add(AnalyticsDimension.TREND);
                default -> { /* Unknown legacy dimensions are ignored rather than expanding the query. */ }
            }
        }
        return result.isEmpty() ? EnumSet.allOf(AnalyticsDimension.class) : result;
    }
}
