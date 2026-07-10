package com.career.platform.position.repository;

import com.career.platform.position.entity.JobPosition;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface PositionRepository extends JpaRepository<JobPosition, Long>, JpaSpecificationExecutor<JobPosition> {

    @Override
    @EntityGraph(attributePaths = "company")
    org.springframework.data.domain.Page<JobPosition> findAll(Specification<JobPosition> specification, Pageable pageable);

    @EntityGraph(attributePaths = "company")
    @Query("select p from JobPosition p order by p.createTime desc")
    List<JobPosition> findLatest(Pageable pageable);

    @EntityGraph(attributePaths = "company")
    @Query("select p from JobPosition p")
    List<JobPosition> findAllWithCompany();

    @EntityGraph(attributePaths = "company")
    List<JobPosition> findByTitleContainingIgnoreCase(String title);

    @Query("select distinct p.title from JobPosition p where lower(p.title) like lower(concat('%', :keyword, '%')) order by p.title")
    List<String> suggestTitles(String keyword, Pageable pageable);
}
