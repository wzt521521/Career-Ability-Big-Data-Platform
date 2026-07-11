package com.career.platform.collect.service;

import com.career.platform.collect.entity.CollectTask;
import com.career.platform.collect.repository.CollectSourceRepository;
import com.career.platform.collect.repository.CollectTaskRepository;
import com.career.platform.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CollectTaskService {

    private final CollectTaskRepository repository;
    private final CollectSourceRepository sourceRepository;

    public CollectTaskService(CollectTaskRepository repository,
                              CollectSourceRepository sourceRepository) {
        this.repository = repository;
        this.sourceRepository = sourceRepository;
    }

    public List<CollectTask> list() {
        return repository.findAll();
    }

    public CollectTask getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("采集任务不存在: " + id));
    }

    public CollectTask create(CollectTask task) {
        if (!sourceRepository.existsById(task.getSourceId())) {
            throw new ResourceNotFoundException("关联数据源不存在: " + task.getSourceId());
        }
        task.setId(null);
        if (task.getStatus() == null) task.setStatus("IDLE");
        if (task.getRetryCount() == null) task.setRetryCount(0);
        if (task.getMaxRetries() == null) task.setMaxRetries(3);
        return repository.save(task);
    }

    public CollectTask update(Long id, CollectTask task) {
        CollectTask exist = getById(id);
        if (task.getSourceId() != null) {
            if (!sourceRepository.existsById(task.getSourceId())) {
                throw new ResourceNotFoundException("关联数据源不存在: " + task.getSourceId());
            }
            exist.setSourceId(task.getSourceId());
        }
        if (task.getTaskName() != null) exist.setTaskName(task.getTaskName());
        if (task.getCronExpression() != null) exist.setCronExpression(task.getCronExpression());
        if (task.getStatus() != null) exist.setStatus(task.getStatus());
        if (task.getMaxRetries() != null) exist.setMaxRetries(task.getMaxRetries());
        return repository.save(exist);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("采集任务不存在: " + id);
        }
        repository.deleteById(id);
    }

    public List<CollectTask> listBySourceId(Long sourceId) {
        return repository.findBySourceId(sourceId);
    }
}
