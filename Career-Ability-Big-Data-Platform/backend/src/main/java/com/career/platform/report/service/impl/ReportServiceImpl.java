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
import com.career.platform.report.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final ReportTemplateRepository templateRepository;
    private final ReportRecordRepository recordRepository;
    private final AsyncReportGenerator asyncReportGenerator;

    public ReportServiceImpl(ReportTemplateRepository templateRepository,
                            ReportRecordRepository recordRepository,
                            AsyncReportGenerator asyncReportGenerator) {
        this.templateRepository = templateRepository;
        this.recordRepository = recordRepository;
        this.asyncReportGenerator = asyncReportGenerator;
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

        ReportRecord record = new ReportRecord();
        record.setTemplateId(template.getId());
        record.setUserId(userId);
        record.setReportTitle(request.getTitle());
        record.setTimeRangeStart(request.getTimeRangeStart());
        record.setTimeRangeEnd(request.getTimeRangeEnd());
        record.setStatus("PENDING");
        record = recordRepository.save(record);

        // 通过注入的独立 Bean 调用 @Async，确保异步生效（避免自调用绕过代理）
        asyncReportGenerator.generate(record.getId(), template.getTemplateFile(), request.getTitle(),
                request.getTimeRangeStart(), request.getTimeRangeEnd());

        return new ReportRecordResponse(record, template.getTemplateName());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportRecordResponse> listRecords(Long userId, String status, String keyword,
                                                          int page, int size) {
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

        File file = new File(record.getFilePath());
        if (!file.exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告文件已被删除");
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(Long userId, Long recordId) {
        ReportRecord record = recordRepository.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("报告记录不存在: " + recordId));

        // 删除文件
        if (record.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(record.getFilePath()));
            } catch (Exception e) {
                log.warn("删除报告文件失败: {}", record.getFilePath(), e);
            }
        }
        recordRepository.delete(record);
    }
}
