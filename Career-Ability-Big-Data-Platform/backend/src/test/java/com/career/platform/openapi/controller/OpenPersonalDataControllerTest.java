package com.career.platform.openapi.controller;

import com.career.platform.common.PageResponse;
import com.career.platform.common.error.BusinessException;
import com.career.platform.common.security.CurrentUser;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.openapi.security.OpenApiCaller;
import com.career.platform.openapi.security.OpenApiRequestContext;
import com.career.platform.profile.dto.ProfileResponse;
import com.career.platform.profile.service.ProfileService;
import com.career.platform.recommend.dto.GapAnalysisResponse;
import com.career.platform.recommend.dto.RecommendationResponse;
import com.career.platform.recommend.service.RecommendService;
import com.career.platform.report.dto.ReportRecordResponse;
import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.service.ReportService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenPersonalDataControllerTest {

    private CurrentUserProvider currentUserProvider;
    private ProfileService profileService;
    private RecommendService recommendService;
    private ReportService reportService;
    private OpenPersonalDataController controller;

    @BeforeEach
    void setUp() {
        currentUserProvider = mock(CurrentUserProvider.class);
        profileService = mock(ProfileService.class);
        recommendService = mock(RecommendService.class);
        reportService = mock(ReportService.class);
        controller = new OpenPersonalDataController(
                currentUserProvider, profileService, recommendService, reportService);
        when(currentUserProvider.requireCurrentUser()).thenReturn(
                new CurrentUser(17L, "student", null, Set.of("ROLE_STUDENT"), Set.of("recommend:view")));
    }

    @Test
    void usesOnlyTheAuthenticatedApiKeyOwnerForEveryPersonalDataEndpoint() {
        MockHttpServletRequest request = requestFor(17L);
        ProfileResponse profile = mock(ProfileResponse.class);
        RecommendationResponse recommendation = mock(RecommendationResponse.class);
        GapAnalysisResponse gap = mock(GapAnalysisResponse.class);
        ReportRecordResponse report = mock(ReportRecordResponse.class);
        ByteArrayResource file = new ByteArrayResource(new byte[]{1});
        when(profileService.getProfile(17L)).thenReturn(profile);
        when(recommendService.recommend(17L, 1, 20)).thenReturn(List.of(recommendation));
        when(recommendService.count(17L)).thenReturn(1L);
        when(recommendService.gapAnalysis(17L, 8L)).thenReturn(gap);
        when(reportService.listRecords(17L, "COMPLETED", "July", 1, 20))
                .thenReturn(new PageResponse<>(List.of(report), 1, 1, 1, 20));
        when(reportService.download(17L, 99L)).thenReturn(file);

        assertEquals(profile, controller.profile(request).getData());
        assertEquals(1L, controller.recommendations(1, 20, request).getData().getTotalElements());
        assertEquals(gap, controller.recommendationGap(8L, request).getData());
        assertEquals(report, controller.reports("COMPLETED", "July", 1, 20, request).getData().getContent().get(0));
        assertEquals(file, controller.downloadReport(99L, request).getBody());

        verify(profileService).getProfile(17L);
        verify(recommendService).recommend(17L, 1, 20);
        verify(recommendService).count(17L);
        verify(recommendService).gapAnalysis(17L, 8L);
        verify(reportService).listRecords(17L, "COMPLETED", "July", 1, 20);
        verify(reportService).download(17L, 99L);
    }

    @Test
    void rejectsAnApiKeyOwnerThatDoesNotMatchTheJwtPrincipal() {
        MockHttpServletRequest request = requestFor(42L);

        assertThrows(BusinessException.class, () -> controller.profile(request));
    }

    @Test
    void doesNotExposeStoredFailureDetailsThroughOpenReports() {
        ReportRecord failedRecord = new ReportRecord();
        failedRecord.setStatus("FAILED");
        failedRecord.setErrorMsg("Unable to render /var/lib/career-ability/reports/private.ftl; token=top-secret");
        ReportRecordResponse response = new ReportRecordResponse(failedRecord);
        when(reportService.listRecords(17L, null, null, 1, 20))
                .thenReturn(new PageResponse<>(List.of(response), 1, 1, 1, 20));

        String message = controller.reports(null, null, 1, 20, requestFor(17L))
                .getData().getContent().get(0).getErrorMsg();

        assertEquals("Report generation failed. Please retry.", message);
        assertFalse(message.contains("/var/lib"));
        assertFalse(message.contains("top-secret"));
    }

    @Test
    void protectsRecommendationsAndReportsWithTheSameMinimumPermissionsAsPrivateControllers() throws Exception {
        assertPermission("recommendations", "recommend:view", int.class, int.class, javax.servlet.http.HttpServletRequest.class);
        assertPermission("recommendationGap", "recommend:view", Long.class, javax.servlet.http.HttpServletRequest.class);
        assertPermission("reports", "report:view", String.class, String.class, int.class, int.class,
                javax.servlet.http.HttpServletRequest.class);
        assertPermission("downloadReport", "report:view", Long.class, javax.servlet.http.HttpServletRequest.class);
    }

    private MockHttpServletRequest requestFor(Long apiKeyOwnerId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(OpenApiRequestContext.CALLER_ATTRIBUTE,
                new OpenApiCaller(3L, apiKeyOwnerId, "integration-test"));
        return request;
    }

    private void assertPermission(String methodName, String permission, Class<?>... parameterTypes) throws Exception {
        PreAuthorize preAuthorize = OpenPersonalDataController.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("hasAuthority('" + permission + "')", preAuthorize.value());
    }
}
