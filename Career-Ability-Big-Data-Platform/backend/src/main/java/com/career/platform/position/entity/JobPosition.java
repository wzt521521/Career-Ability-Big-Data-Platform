package com.career.platform.position.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_position")
public class JobPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true, length = 100)
    private String jobId;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private JobCompany company;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String province;

    @Column(name = "city_tier", length = 20)
    private String cityTier;

    @Column(length = 20)
    private String education;

    @Column(length = 20)
    private String experience;

    @Convert(converter = StringListJsonConverter.class)
    @Column
    private List<String> skills = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column
    private List<String> welfare = new ArrayList<>();

    @Lob
    private String description;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public JobCompany getCompany() { return company; }
    public void setCompany(JobCompany company) { this.company = company; }
    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCityTier() { return cityTier; }
    public void setCityTier(String cityTier) { this.cityTier = cityTier; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public List<String> getWelfare() { return welfare; }
    public void setWelfare(List<String> welfare) { this.welfare = welfare; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getPublishDate() { return publishDate; }
    public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public LocalDateTime getCreateTime() { return createTime; }
}
