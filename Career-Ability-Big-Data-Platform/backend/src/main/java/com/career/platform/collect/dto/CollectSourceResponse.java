package com.career.platform.collect.dto;

import com.career.platform.collect.entity.CollectSource;
import java.time.LocalDateTime;

/** Public representation of a source configuration. */
public class CollectSourceResponse {

    private Long id;
    private String sourceName;
    private String sourceType;
    private String filePath;
    private String fieldMapping;
    private String importFrequency;
    private Integer status;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static CollectSourceResponse from(CollectSource source) {
        CollectSourceResponse response = new CollectSourceResponse();
        response.id = source.getId();
        response.sourceName = source.getSourceName();
        response.sourceType = source.getSourceType();
        response.filePath = source.getFilePath();
        response.fieldMapping = source.getFieldMapping();
        response.importFrequency = source.getImportFrequency();
        response.status = source.getStatus();
        response.description = source.getDescription();
        response.createTime = source.getCreateTime();
        response.updateTime = source.getUpdateTime();
        return response;
    }

    public Long getId() { return id; }
    public String getSourceName() { return sourceName; }
    public String getSourceType() { return sourceType; }
    public String getFilePath() { return filePath; }
    public String getFieldMapping() { return fieldMapping; }
    public String getImportFrequency() { return importFrequency; }
    public Integer getStatus() { return status; }
    public String getDescription() { return description; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
