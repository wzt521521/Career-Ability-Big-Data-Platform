package com.career.platform.report.controller;

import com.career.platform.common.ApiResponse;
import com.career.platform.common.PageResponse;
import com.career.platform.common.security.CurrentUser;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.report.dto.GenerateReportRequest;
import com.career.platform.report.dto.ReportRecordResponse;
import com.career.platform.report.dto.ReportTemplateResponse;
import com.career.platform.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    private ReportService reportService;
    private CurrentUserProvider currentUserProvider;
    private ReportController controller;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        controller = new ReportController(reportService, currentUserProvider);
        when(currentUserProvider.requireCurrentUser()).thenReturn(
                new CurrentUser(17L, "student", null, Set.of("ROLE_STUDENT"), Set.of("report:view")));
    }

    @Test
    void usesTheCurrentUserForAllUserScopedReportOperations() {
        GenerateReportRequest request = new GenerateReportRequest();
        request.setTemplateId(3L);
        request.setTitle("monthly");
        ReportRecordResponse record = mock(ReportRecordResponse.class);
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(reportService.generate(17L, request)).thenReturn(record);
        when(reportService.listRecords(17L, "PENDING", "monthly", 1, 10))
                .thenReturn(new PageResponse<>(List.of(record), 1, 1, 1, 10));
        when(reportService.getStatus(17L, 88L)).thenReturn(record);
        when(reportService.download(17L, 88L)).thenReturn(resource);

        ApiResponse<ReportRecordResponse> generated = controller.generate(request);
        ApiResponse<PageResponse<ReportRecordResponse>> listed = controller.list("PENDING", "monthly", 1, 10);
        ApiResponse<ReportRecordResponse> status = controller.status(88L);
        ResponseEntity<Resource> download = controller.download(88L);
        ResponseEntity<Resource> preview = controller.preview(88L);
        controller.delete(88L);

        assertEquals(record, generated.getData());
        assertEquals(record, listed.getData().getContent().get(0));
        assertEquals(record, status.getData());
        assertEquals(MediaType.APPLICATION_PDF, download.getHeaders().getContentType());
        assertEquals(resource, download.getBody());
        assertEquals(resource, preview.getBody());
        verify(reportService).generate(17L, request);
        verify(reportService).listRecords(17L, "PENDING", "monthly", 1, 10);
        verify(reportService).getStatus(17L, 88L);
        verify(reportService, times(2)).download(17L, 88L);
        verify(reportService).delete(17L, 88L);
    }

    @Test
    void protectsEveryReportEndpointWithReportViewPermission() throws Exception {
        assertPermission(ReportController.class.getMethod("getTemplates"), "report:view");
        assertPermission(ReportController.class.getMethod("generate", GenerateReportRequest.class), "report:generate");
        assertPermission(ReportController.class.getMethod("list", String.class, String.class, int.class, int.class), "report:view");
        assertPermission(ReportController.class.getMethod("status", Long.class), "report:view");
        assertPermission(ReportController.class.getMethod("download", Long.class), "report:view");
        assertPermission(ReportController.class.getMethod("preview", Long.class), "report:view");
        assertPermission(ReportController.class.getMethod("delete", Long.class), "report:delete");
    }

    private void assertPermission(Method method, String permission) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("hasAuthority('" + permission + "')", preAuthorize.value());
    }
}
