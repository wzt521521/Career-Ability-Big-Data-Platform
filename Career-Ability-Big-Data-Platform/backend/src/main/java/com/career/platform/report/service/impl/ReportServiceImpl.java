package com.career.platform.report.service.impl;

import com.career.platform.common.PageResponse;
import com.career.platform.common.ResourceNotFoundException;
import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import com.career.platform.report.dto.GenerateReportRequest;
import com.career.platform.report.dto.ReportRecordResponse;
import com.career.platform.report.dto.ReportTemplateResponse;
import com.career.platform.report.entity.ReportRecord;
import com.career.platform.report.entity.ReportTemplate;
import com.career.platform.report.repository.ReportRecordRepository;
import com.career.platform.report.repository.ReportTemplateRepository;
import com.career.platform.report.service.AsyncReportGenerator;
import com.career.platform.report.service.ReportGenerationRequestedEvent;
import com.career.platform.report.service.ReportService;
import com.career.platform.report.service.ReportSnapshotMapper;
import com.career.platform.report.service.ReportStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final ReportTemplateRepository templateRepository;
    private final ReportRecordRepository recordRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportSnapshotMapper snapshotMapper;
    private final ReportStorage reportStorage;

    public ReportServiceImpl(ReportTemplateRepository templateRepository,
                            ReportRecordRepository recordRepository,
                            ApplicationEventPublisher eventPublisher,
                            ReportSnapshotMapper snapshotMapper,
                            ReportStorage reportStorage) {
        this.templateRepository = templateRepository;
        this.recordRepository = recordRepository;
        this.eventPublisher = eventPublisher;
        this.snapshotMapper = snapshotMapper;
        this.reportStorage = reportStorage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportTemplateResponse> getTemplates() {
        return templateRepository.findByStatusOrderByIsDefaultDescCreateTimeAsc(1).stream()
                .map(ReportTemplateResponse::new)
                .collect(Collectors.toList());
    }

    @Override
    public ReportRecordResponse generate(Long userId, GenerateReportRequest request) {
        ReportTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告模板不存在"));
        if (!Integer.valueOf(1).equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告模板已禁用");
        }

        ReportRecord record = new ReportRecord();
        record.setTemplateId(template.getId());
        record.setUserId(userId);
        record.setReportTitle(request.getTitle().trim());
        record.setTimeRangeStart(request.getTimeRangeStart());
        record.setTimeRangeEnd(request.getTimeRangeEnd());
        record.setFilterCity(normalizeFilter(request.getCity()));
        record.setFilterPosition(normalizeFilter(request.getPosition()));
        record.setFilterIndustry(normalizeFilter(request.getIndustry()));
        record.setAnalysisDimensions(snapshotMapper.serializeDimensions(template.getDimensions()));
        record.setStatus("PENDING");
        record = recordRepository.save(record);

        // TransactionalEventListener dispatches only after this transaction commits.
        eventPublisher.publishEvent(new ReportGenerationRequestedEvent(record.getId()));

        return new ReportRecordResponse(record, template.getTemplateName());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportRecordResponse> listRecords(Long userId, String status, String keyword,
                                                          int page, int size) {
        validateListRequest(status, keyword, page, size);
        PageRequest pageable = PageRequest.of(page - 1, size);
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        Page<ReportRecord> result;
        if (hasStatus && hasKeyword) {
            result = recordRepository.findByUserIdAndStatusAndReportTitleContainingOrderByCreateTimeDesc(
                    userId, status, keyword, pageable);
        } else if (hasStatus) {
            result = recordRepository.findByUserIdAndStatusOrderByCreateTimeDesc(userId, status, pageable);
        } else if (hasKeyword) {
            result = recordRepository.findByUserIdAndReportTitleContainingOrderByCreateTimeDesc(
                    userId, keyword, pageable);
        } else {
            result = recordRepository.findByUserIdOrderByCreateTimeDesc(userId, pageable);
        }

        // 批量查询模板名称，避免 N+1
        Map<Long, String> templateNameMap = templateRepository.findAllById(
                result.getContent().stream()
                        .map(ReportRecord::getTemplateId)
                        .distinct()
                        .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(ReportTemplate::getId, ReportTemplate::getTemplateName));

        List<ReportRecordResponse> content = result.getContent().stream()
                .map(record -> new ReportRecordResponse(record,
                        templateNameMap.getOrDefault(record.getTemplateId(), "未知模板")))
                .collect(Collectors.toList());
        return PageResponse.from(result, content);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportRecordResponse getStatus(Long userId, Long recordId) {
        ReportRecord record = recordRepository.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("报告记录不存在: " + recordId));
        String templateName = templateRepository.findById(record.getTemplateId())
                .map(ReportTemplate::getTemplateName)
                .orElse(null);
        return new ReportRecordResponse(record, templateName);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource download(Long userId, Long recordId) {
        ReportRecord record = recordRepository.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("报告记录不存在: " + recordId));

        if (!"COMPLETED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告尚未生成完成");
        }
        if (record.getFilePath() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告文件不存在");
        }

        Path file = reportStorage.resolveExisting(record.getFilePath());
        return new FileSystemResource(file);
    }

    @Override
    public void delete(Long userId, Long recordId) {
        ReportRecord record = recordRepository.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("报告记录不存在: " + recordId));

        // 删除文件
        if (record.getFilePath() != null) {
            try {
                reportStorage.deleteIfManaged(record.getFilePath());
            } catch (Exception e) {
                log.warn("删除报告文件失败: {}", record.getFilePath(), e);
            }
        }
        recordRepository.delete(record);
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateListRequest(String status, String keyword, int page, int size) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "page 必须大于等于 1");
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "size 必须在 1 到 100 之间");
        }
        if (status != null && !status.isBlank()
                && !Set.of("PENDING", "GENERATING", "COMPLETED", "FAILED").contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的报告状态");
        }
        if (keyword != null && keyword.length() > 120) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告标题关键字最长120字符");
        }
    }
}
