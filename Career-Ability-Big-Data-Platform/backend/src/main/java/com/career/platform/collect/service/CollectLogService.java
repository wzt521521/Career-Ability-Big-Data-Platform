package com.career.platform.collect.service;

import com.career.platform.collect.entity.CollectLog;
import com.career.platform.collect.repository.CollectLogRepository;
import com.career.platform.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CollectLogService {

    private final CollectLogRepository repository;

    public CollectLogService(CollectLogRepository repository) {
        this.repository = repository;
    }

    public List<CollectLog> list() {
        return repository.findAll();
    }

    public CollectLog getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("执行日志不存在: " + id));
    }

    public List<CollectLog> listByTaskId(Long taskId) {
        return repository.findByTaskIdOrderByStartTimeDesc(taskId);
    }
}
