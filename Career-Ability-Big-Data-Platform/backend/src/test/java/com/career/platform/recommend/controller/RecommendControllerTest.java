package com.career.platform.recommend.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.common.security.CurrentUser;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.recommend.dto.GapAnalysisResponse;
import com.career.platform.recommend.dto.RecommendationResponse;
import com.career.platform.recommend.service.RecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendControllerTest {

    private RecommendService recommendService;
    private CurrentUserProvider currentUserProvider;
    private RecommendController controller;

    @BeforeEach
    void setUp() {
        recommendService = mock(RecommendService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        controller = new RecommendController(recommendService, currentUserProvider);
        when(currentUserProvider.requireCurrentUser()).thenReturn(
                new CurrentUser(17L, "student", null, Set.of("ROLE_STUDENT"), Set.of("recommend:view")));
    }

    @Test
    void usesTheCurrentUserForRecommendationAndGapRequests() {
        RecommendationResponse recommendation = mock(RecommendationResponse.class);
        GapAnalysisResponse gap = mock(GapAnalysisResponse.class);
        when(recommendService.recommend(17L, 1, 20)).thenReturn(List.of(recommendation));
        when(recommendService.count(17L)).thenReturn(1L);
        when(recommendService.gapAnalysis(17L, 88L)).thenReturn(gap);

        ApiResponse<PageResponse<RecommendationResponse>> page = controller.recommend(1, 20);
        ApiResponse<GapAnalysisResponse> gapResponse = controller.gapAnalysis(88L);

        assertEquals(1L, page.getData().getTotalElements());
        assertEquals(recommendation, page.getData().getContent().get(0));
        assertEquals(gap, gapResponse.getData());
        verify(recommendService).recommend(17L, 1, 20);
        verify(recommendService).count(17L);
        verify(recommendService).gapAnalysis(17L, 88L);
    }

    @Test
    void protectsEveryRecommendationEndpointWithRecommendViewPermission() throws Exception {
        assertPermission(RecommendController.class.getMethod("recommend", int.class, int.class));
        assertPermission(RecommendController.class.getMethod("gapAnalysis", Long.class));
    }

    private void assertPermission(Method method) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("hasAuthority('recommend:view')", preAuthorize.value());
    }
}
