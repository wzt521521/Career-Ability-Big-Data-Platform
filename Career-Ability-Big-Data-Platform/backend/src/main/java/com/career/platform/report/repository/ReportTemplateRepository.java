package com.career.platform.report.repository;

import com.career.platform.report.entity.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    List<ReportTemplate> findByStatusOrderByIsDefaultDescCreateTimeAsc(Integer status);
}
