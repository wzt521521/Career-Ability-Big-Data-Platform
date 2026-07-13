package com.career.platform.report.dto;

import com.career.platform.report.entity.ReportTemplate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "报告模板响应")
public class ReportTemplateResponse {

    @Schema(description = "模板ID")
    private final Long id;

    @Schema(description = "模板名称")
    private final String templateName;

    @Schema(description = "模板类型")
    private final String templateType;

    @Schema(description = "描述")
    private final String description;

    @Schema(description = "包含的分析维度")
    private final List<String> dimensions;

    public ReportTemplateResponse(ReportTemplate template) {
        this.id = template.getId();
        this.templateName = template.getTemplateName();
        this.templateType = template.getTemplateType();
        this.description = template.getDescription();
        this.dimensions = template.getDimensions();
    }

    public Long getId() { return id; }
    public String getTemplateName() { return templateName; }
    public String getTemplateType() { return templateType; }
    public String getDescription() { return description; }
    public List<String> getDimensions() { return dimensions; }
}
