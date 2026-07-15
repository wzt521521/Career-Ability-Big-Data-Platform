package com.career.platform.collect.repository;

import com.career.platform.collect.entity.CollectTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CollectTaskRepository extends JpaRepository<CollectTask, Long> {
    List<CollectTask> findBySourceId(Long sourceId);
    List<CollectTask> findBySourceId(Long sourceId, Pageable pageable);
    List<CollectTask> findByStatus(String status);
}
