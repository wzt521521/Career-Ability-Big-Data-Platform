package com.career.platform.position.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class PositionFilter {
    @Size(max = 100, message = "keyword 长度不能超过 100")
    private String keyword;
    @Size(max = 50, message = "city 长度不能超过 50")
    private String city;
    @Min(value = 0, message = "salaryMin 不能为负数")
    @Max(value = 1000, message = "salaryMin 不能超过 1000")
    private Integer salaryMin;
    @Min(value = 0, message = "salaryMax 不能为负数")
    @Max(value = 1000, message = "salaryMax 不能超过 1000")
    private Integer salaryMax;
    @Size(max = 20, message = "education 长度不能超过 20")
    private String education;
    @Size(max = 20, message = "experience 长度不能超过 20")
    private String experience;
    @Size(max = 100, message = "industry 长度不能超过 100")
    private String industry;
    @Pattern(regexp = "publishDate|createTime|salaryMin|salaryMax|title", message = "不支持的排序字段")
    private String sortBy = "publishDate";
    @Pattern(regexp = "(?i)asc|desc", message = "sortDirection 仅支持 asc 或 desc")
    private String sortDirection = "desc";
    @Min(value = 1, message = "page 必须大于等于 1")
    @Max(value = 100000, message = "page 不能超过 100000")
    private int page = 1;
    @Min(value = 1, message = "size 必须在 1 到 100 之间")
    @Max(value = 100, message = "size 必须在 1 到 100 之间")
    private int size = 20;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
