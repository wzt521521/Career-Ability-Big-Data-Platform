package com.career.platform.report.service;

import com.career.platform.analytics.dto.AnalyticsDimension;
import com.career.platform.analytics.dto.AnalyticsSnapshotRequest;
import com.career.platform.analytics.dto.AnalyticsSnapshotResponse;
import com.career.platform.common.security.PublicRecruitmentScope;
import com.career.platform.report.entity.ReportRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportSnapshotMapperTest {

    @Test
    void mapsPersistedFiltersAndTemplateDimensionsToTheAnalyticsSnapshotContract() {
        ReportSnapshotMapper mapper = new ReportSnapshotMapper(new ObjectMapper().findAndRegisterModules());
        ReportRecord record = new ReportRecord();
        record.setReportTitle("2026 monthly report");
        record.setTimeRangeStart(LocalDate.of(2026, 7, 1));
        record.setTimeRangeEnd(LocalDate.of(2026, 7, 31));
        record.setFilterCity("Shanghai");
        record.setFilterPosition("Engineer");
        record.setFilterIndustry("Software");
        record.setAnalysisDimensions("position,salary,industry");

        AnalyticsSnapshotRequest request = mapper.toSnapshotRequest(record);

        assertEquals(record.getTimeRangeStart(), request.getStartDate());
        assertEquals(record.getTimeRangeEnd(), request.getEndDate());
        assertEquals("Shanghai", request.getCity());
        assertTrue(request.getDimensions().contains(AnalyticsDimension.POSITION));
        assertTrue(request.getDimensions().contains(AnalyticsDimension.SALARY));
        assertTrue(request.getDimensions().contains(AnalyticsDimension.COMPANY));
        assertFalse(request.getDimensions().contains(AnalyticsDimension.SKILL));
    }

    @Test
    void emitsStableTemplateDataAndSerializableActualScope() {
        ReportSnapshotMapper mapper = new ReportSnapshotMapper(new ObjectMapper().findAndRegisterModules());
        ReportRecord record = new ReportRecord();
        record.setReportTitle("<script>alert(1)</script>");
        record.setFilterCity("Shanghai");
        record.setAnalysisDimensions("skill");
        AnalyticsSnapshotResponse snapshot = new AnalyticsSnapshotResponse(
                PublicRecruitmentScope.shared(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                java.util.EnumSet.of(AnalyticsDimension.SKILL),
                Map.of("overview", Map.of("totalPositions", 3), "skills", Map.of("topSkills", java.util.List.of())),
                false,
                LocalDateTime.of(2026, 7, 31, 12, 0));

        Map<String, Object> model = mapper.toTemplateModel(snapshot, record);
        String scope = mapper.serializeScope(snapshot, record);

        assertEquals(record.getReportTitle(), model.get("reportTitle"));
        assertEquals("2026-07-01", model.get("timeRangeStart"));
        assertTrue(model.containsKey("salary"));
        assertTrue(scope.contains("PUBLIC_RECRUITMENT"));
        assertTrue(scope.contains("Shanghai"));
    }
}
