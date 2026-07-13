package com.career.platform.report.service;

import com.career.platform.common.PageResponse;
import com.career.platform.report.dto.GenerateReportRequest;
import com.career.platform.report.dto.ReportRecordResponse;
import com.career.platform.report.dto.ReportTemplateResponse;
import org.springframework.core.io.Resource;

import java.util.List;

public interface ReportService {

    /**
     * 获取所有启用的报告模板。
     */
    List<ReportTemplateResponse> getTemplates();

    /**
     * 异步生成报告。立即返回 PENDING 状态的记录。
     */
    ReportRecordResponse generate(Long userId, GenerateReportRequest request);

    /**
     * 分页查询当前用户的报告记录，支持状态筛选、关键字搜索和日期范围过滤。
     *
     * @param keyword   报告标题关键字（模糊匹配，可为 null）
     */
    PageResponse<ReportRecordResponse> listRecords(Long userId, String status, String keyword, int page, int size);

    /**
     * 查询报告状态（用于前端轮询）。
     */
    ReportRecordResponse getStatus(Long userId, Long recordId);

    /**
     * 下载报告文件。
     */
    Resource download(Long userId, Long recordId);

    /**
     * 删除报告记录及文件。
     */
    void delete(Long userId, Long recordId);
}
