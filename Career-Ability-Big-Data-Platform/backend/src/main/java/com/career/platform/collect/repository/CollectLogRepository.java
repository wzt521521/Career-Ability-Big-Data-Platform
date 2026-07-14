package com.career.platform.collect.repository;

import com.career.platform.collect.entity.CollectLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CollectLogRepository extends JpaRepository<CollectLog, Long> {
    List<CollectLog> findByTaskIdOrderByStartTimeDesc(Long taskId);
    List<CollectLog> findByTaskIdOrderByStartTimeDesc(Long taskId, Pageable pageable);
}
