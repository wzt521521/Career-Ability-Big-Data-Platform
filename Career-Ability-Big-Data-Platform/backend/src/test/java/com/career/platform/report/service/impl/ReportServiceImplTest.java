package com.career.platform.report.service.impl;

import com.career.platform.common.ResourceNotFoundException;
import com.career.platform.common.error.BusinessException;
import com.career.platform.report.dto.GenerateReportRequest;
import com.career.platform.report.dto.ReportRecordResponse;
import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.entity.ReportTemplate;
import com.career.platform.report.repository.ReportRecordRepository;
import com.career.platform.report.repository.ReportTemplateRepository;
import com.career.platform.report.service.ReportGenerationRequestedEvent;
import com.career.platform.report.service.ReportSnapshotMapper;
import com.career.platform.report.service.ReportStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceImplTest {

    private ReportTemplateRepository templateRepository;
    private ReportRecordRepository recordRepository;
    private ApplicationEventPublisher eventPublisher;
    private ReportSnapshotMapper snapshotMapper;
    private ReportStorage reportStorage;
    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        templateRepository = mock(ReportTemplateRepository.class);
        recordRepository = mock(ReportRecordRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        snapshotMapper = mock(ReportSnapshotMapper.class);
        reportStorage = mock(ReportStorage.class);
        service = new ReportServiceImpl(templateRepository, recordRepository, eventPublisher, snapshotMapper, reportStorage);
    }

    @Test
    void createsPendingRecordAndPublishesGenerationOnlyAfterPersistence() {
        ReportTemplate template = template(7L, "monthly.ftl");
        GenerateReportRequest request = request(7L, "monthly report");
        when(templateRepository.findById(7L)).thenReturn(Optional.of(template));
        when(recordRepository.save(any(ReportRecord.class))).thenAnswer(invocation -> {
            ReportRecord record = invocation.getArgument(0);
            record.setId(99L);
            return record;
        });

        ReportRecordResponse response = service.generate(41L, request);

        assertEquals(99L, response.getId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("monthly report", response.getReportTitle());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getValue() instanceof ReportGenerationRequestedEvent);
        assertEquals(99L, ((ReportGenerationRequestedEvent) eventCaptor.getValue()).getReportId());
    }

    @Test
    void returnsCompletedStatusWithTemplateName() {
        ReportRecord record = record(99L, 41L, "COMPLETED");
        ReportTemplate template = template(7L, "monthly.ftl");
        template.setTemplateName("Monthly");
        when(recordRepository.findByIdAndUserId(99L, 41L)).thenReturn(Optional.of(record));
        when(templateRepository.findById(7L)).thenReturn(Optional.of(template));

        ReportRecordResponse response = service.getStatus(41L, 99L);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals("Monthly", response.getTemplateName());
    }

    @Test
    void downloadsOnlyCompletedReports(@TempDir Path temporaryDirectory) throws IOException {
        Path reportFile = Files.write(temporaryDirectory.resolve("report.pdf"), new byte[]{1, 2, 3});
        ReportRecord completed = record(99L, 41L, "COMPLETED");
        completed.setFilePath(reportFile.toString());
        when(recordRepository.findByIdAndUserId(99L, 41L)).thenReturn(Optional.of(completed));
        when(reportStorage.resolveExisting(reportFile.toString())).thenReturn(reportFile);

        Resource resource = service.download(41L, 99L);

        assertTrue(resource.exists());
        assertEquals(3, resource.contentLength());
    }

    @Test
    void rejectsFailedReportDownload() {
        ReportRecord failed = record(99L, 41L, "FAILED");
        when(recordRepository.findByIdAndUserId(99L, 41L)).thenReturn(Optional.of(failed));

        assertThrows(BusinessException.class, () -> service.download(41L, 99L));
    }

    @Test
    void rejectsPendingReportDownload() {
        ReportRecord pending = record(99L, 41L, "PENDING");
        when(recordRepository.findByIdAndUserId(99L, 41L)).thenReturn(Optional.of(pending));

        assertThrows(BusinessException.class, () -> service.download(41L, 99L));
    }

    @Test
    void rejectsStatusDownloadAndDeleteForAnotherUser() {
        when(recordRepository.findByIdAndUserId(99L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getStatus(42L, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.download(42L, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(42L, 99L));

        verify(recordRepository, never()).delete(any(ReportRecord.class));
    }

    private GenerateReportRequest request(Long templateId, String title) {
        GenerateReportRequest request = new GenerateReportRequest();
        request.setTemplateId(templateId);
        request.setTitle(title);
        request.setTimeRangeStart(LocalDate.of(2026, 1, 1));
        request.setTimeRangeEnd(LocalDate.of(2026, 1, 31));
        return request;
    }

    private ReportTemplate template(Long id, String file) {
        ReportTemplate template = new ReportTemplate();
        template.setId(id);
        template.setTemplateName("Template");
        template.setTemplateType("monthly");
        template.setTemplateFile(file);
        return template;
    }

    private ReportRecord record(Long id, Long userId, String status) {
        ReportRecord record = new ReportRecord();
        record.setId(id);
        record.setUserId(userId);
        record.setTemplateId(7L);
        record.setReportTitle("monthly report");
        record.setStatus(status);
        return record;
    }
}
