package com.career.platform.position.repository;

import com.career.platform.position.entity.JobCompany;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<JobCompany, Long> {
}
