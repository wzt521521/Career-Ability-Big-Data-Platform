package com.career.platform.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

@Schema(description = "学生画像创建/更新请求")
public class ProfileRequest {

    @Size(max = 100, message = "专业名称最长100字符")
    @Schema(description = "专业名称", example = "计算机科学与技术")
    private String major;

    @Size(max = 30, message = "技能最多30项")
    @Schema(description = "已掌握技能列表", example = "[\"Java\", \"Spring Boot\", \"MySQL\"]")
    private List<@NotBlank(message = "技能名称不能为空") @Size(max = 50, message = "单项技能最长50字符") String> skills;

    @Size(max = 20, message = "学历最长20字符")
    @Pattern(regexp = "^(不限|大专|本科|硕士|博士)(及以上)?$", message = "学历仅支持不限、大专、本科、硕士或博士")
    @Schema(description = "学历", example = "本科")
    private String education;

    @Size(max = 200, message = "意向城市最长200字符")
    @Pattern(regexp = "^[\\p{IsHan}A-Za-z0-9,，、\\s-]*$", message = "意向城市只允许城市名称和分隔符")
    @Schema(description = "意向城市（逗号分隔）", example = "北京,上海,杭州")
    private String preferredCity;

    @Min(value = 0, message = "最低薪资不能为负数")
    @Max(value = 200, message = "最低薪资不能超过200K")
    @Schema(description = "期望最低月薪（K）", example = "8")
    private Integer salaryMin;

    @Min(value = 0, message = "最高薪资不能为负数")
    @Max(value = 200, message = "最高薪资不能超过200K")
    @Schema(description = "期望最高月薪（K）", example = "20")
    private Integer salaryMax;

    /**
     * 校验 salaryMin <= salaryMax（仅当两者均非 null 时触发）。
     * 返回 true 表示通过，false 由全局异常处理转为 400。
     */
    @AssertTrue(message = "最低薪资不能高于最高薪资")
    public boolean isSalaryRangeValid() {
        if (salaryMin == null || salaryMax == null) {
            return true;
        }
        return salaryMin <= salaryMax;
    }

    @AssertTrue(message = "意向城市最多10个，每个城市最长30字符")
    public boolean isPreferredCityValid() {
        if (preferredCity == null || preferredCity.isBlank()) {
            return true;
        }
        String[] cities = preferredCity.split("[,，、\\s]+");
        if (cities.length > 10) {
            return false;
        }
        for (String city : cities) {
            if (city.trim().length() > 30) {
                return false;
            }
        }
        return true;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getPreferredCity() {
        return preferredCity;
    }

    public void setPreferredCity(String preferredCity) {
        this.preferredCity = preferredCity;
    }

    public Integer getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(Integer salaryMin) {
        this.salaryMin = salaryMin;
    }

    public Integer getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(Integer salaryMax) {
        this.salaryMax = salaryMax;
    }
}
