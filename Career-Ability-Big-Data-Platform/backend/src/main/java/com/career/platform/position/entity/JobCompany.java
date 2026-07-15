package com.career.platform.position.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_company", indexes = {
        @Index(name = "idx_jc_industry", columnList = "industry")
})
public class JobCompany {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "company_size", length = 50)
    private String companySize;

    @Column(length = 100)
    private String industry;

    @Column(name = "company_type", length = 50)
    private String companyType;

    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getCompanySize() { return companySize; }
    public void setCompanySize(String companySize) { this.companySize = companySize; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getCompanyType() { return companyType; }
    public void setCompanyType(String companyType) { this.companyType = companyType; }
    public LocalDateTime getCreateTime() { return createTime; }
}
