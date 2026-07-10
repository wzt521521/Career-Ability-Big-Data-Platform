package com.career.platform.position.dto;

import com.career.platform.position.entity.JobCompany;
import com.career.platform.position.entity.JobPosition;

import java.time.LocalDate;
import java.util.List;

public class PositionResponse {
    private final Long id;
    private final String title;
    private final String companyName;
    private final String companySize;
    private final String industry;
    private final String companyType;
    private final Integer salaryMin;
    private final Integer salaryMax;
    private final String city;
    private final String province;
    private final String cityTier;
    private final String education;
    private final String experience;
    private final List<String> skills;
    private final List<String> welfare;
    private final String description;
    private final LocalDate publishDate;
    private final String sourceUrl;

    public PositionResponse(JobPosition position, boolean details) {
        JobCompany company = position.getCompany();
        this.id = position.getId();
        this.title = position.getTitle();
        this.companyName = company == null ? null : company.getCompanyName();
        this.companySize = company == null ? null : company.getCompanySize();
        this.industry = company == null ? null : company.getIndustry();
        this.companyType = company == null ? null : company.getCompanyType();
        this.salaryMin = position.getSalaryMin();
        this.salaryMax = position.getSalaryMax();
        this.city = position.getCity();
        this.province = position.getProvince();
        this.cityTier = position.getCityTier();
        this.education = position.getEducation();
        this.experience = position.getExperience();
        this.skills = position.getSkills() == null ? List.of() : List.copyOf(position.getSkills());
        this.welfare = position.getWelfare() == null ? List.of() : List.copyOf(position.getWelfare());
        this.description = details ? position.getDescription() : null;
        this.publishDate = position.getPublishDate();
        this.sourceUrl = details ? position.getSourceUrl() : null;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getCompanyName() { return companyName; }
    public String getCompanySize() { return companySize; }
    public String getIndustry() { return industry; }
    public String getCompanyType() { return companyType; }
    public Integer getSalaryMin() { return salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }
    public String getCity() { return city; }
    public String getProvince() { return province; }
    public String getCityTier() { return cityTier; }
    public String getEducation() { return education; }
    public String getExperience() { return experience; }
    public List<String> getSkills() { return skills; }
    public List<String> getWelfare() { return welfare; }
    public String getDescription() { return description; }
    public LocalDate getPublishDate() { return publishDate; }
    public String getSourceUrl() { return sourceUrl; }
}
