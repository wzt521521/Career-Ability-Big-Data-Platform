package com.career.platform.collect.repository;

import com.career.platform.collect.entity.CollectSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectSourceRepository extends JpaRepository<CollectSource, Long> {
}
