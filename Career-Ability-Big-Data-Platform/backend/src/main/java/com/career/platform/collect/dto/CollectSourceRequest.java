package com.career.platform.collect.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Mutable source configuration accepted by the collection API.
 * File sources are intentionally limited to the ETL data volume.
 */
@JsonIgnoreProperties({"id", "createTime", "updateTime"})
public class CollectSourceRequest {

    public interface Create {
    }

    @NotBlank(groups = Create.class, message = "数据源名称不能为空")
    @Size(max = 100, message = "数据源名称不能超过 100 个字符")
    private String sourceName;

    @NotBlank(groups = Create.class, message = "数据源类型不能为空")
    @Pattern(regexp = "(?i)^(FILE|URL)$", message = "数据源类型只能是 FILE 或 URL")
    private String sourceType;

    @NotBlank(groups = Create.class, message = "数据源路径不能为空")
    @Size(max = 500, message = "数据源路径不能超过 500 个字符")
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "数据源路径不能包含控制字符")
    private String filePath;

    @Size(max = 10_000, message = "字段映射不能超过 10000 个字符")
    private String fieldMapping;

    @Pattern(regexp = "(?i)^(ONCE|DAILY|WEEKLY|MONTHLY|MANUAL)$",
            message = "导入频率只能是 ONCE、DAILY、WEEKLY、MONTHLY 或 MANUAL")
    private String importFrequency;

    @Min(value = 0, message = "数据源状态只能是 0 或 1")
    @Max(value = 1, message = "数据源状态只能是 0 或 1")
    private Integer status;

    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String description;

    @AssertTrue(message = "FILE 数据源必须使用 /data 下的安全相对路径，URL 数据源必须使用无凭据的 HTTP(S) URL")
    @JsonIgnore
    public boolean isPathAllowedForSourceType() {
        // Partial PUT requests are checked again after they are merged with the persisted source.
        if (sourceType == null || filePath == null) {
            return true;
        }
        return isPathAllowed(sourceType, filePath);
    }

    public static boolean isPathAllowed(String sourceType, String filePath) {
        if (sourceType == null || filePath == null || filePath.isBlank()) {
            return false;
        }

        String normalizedType = sourceType.trim().toUpperCase(Locale.ROOT);
        if ("FILE".equals(normalizedType)) {
            return isManagedFilePath(filePath);
        }
        if ("URL".equals(normalizedType)) {
            return isHttpUrl(filePath);
        }
        return false;
    }

    private static boolean isManagedFilePath(String filePath) {
        if (!filePath.startsWith("/data/") || filePath.indexOf('\\') >= 0) {
            return false;
        }

        String relativePath = filePath.substring("/data/".length());
        if (relativePath.isBlank()) {
            return false;
        }

        for (String segment : relativePath.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || !segment.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHttpUrl(String filePath) {
        try {
            URI uri = new URI(filePath);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFieldMapping() {
        return fieldMapping;
    }

    public void setFieldMapping(String fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    public String getImportFrequency() {
        return importFrequency;
    }

    public void setImportFrequency(String importFrequency) {
        this.importFrequency = importFrequency;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
