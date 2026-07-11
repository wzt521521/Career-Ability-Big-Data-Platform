package com.career.platform.collect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "collect_source")
public class CollectSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "field_mapping", columnDefinition = "JSON")
    private String fieldMapping;

    @Column(name = "import_frequency", length = 20)
    private String importFrequency;

    @Column(nullable = false)
    private Integer status;

    @Column(length = 500)
    private String description;

    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", insertable = false, updatable = false)
    private LocalDateTime updateTime;

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFieldMapping() { return fieldMapping; }
    public void setFieldMapping(String fieldMapping) { this.fieldMapping = fieldMapping; }
    public String getImportFrequency() { return importFrequency; }
    public void setImportFrequency(String importFrequency) { this.importFrequency = importFrequency; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
