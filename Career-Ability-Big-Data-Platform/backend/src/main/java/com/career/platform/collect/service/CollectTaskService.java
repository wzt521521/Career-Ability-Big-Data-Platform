package com.career.platform.collect.service;

import com.career.platform.collect.dto.CollectTaskRequest;
import com.career.platform.collect.dto.CollectTaskResponse;
import com.career.platform.collect.entity.CollectTask;
import com.career.platform.collect.repository.CollectSourceRepository;
import com.career.platform.collect.repository.CollectTaskRepository;
import com.career.platform.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
public class CollectTaskService {

    private final CollectTaskRepository repository;
    private final CollectSourceRepository sourceRepository;
    private final CollectTaskRuntimeService runtimeService;

    public CollectTaskService(CollectTaskRepository repository,
                              CollectSourceRepository sourceRepository,
                              CollectTaskRuntimeService runtimeService) {
        this.repository = repository;
        this.sourceRepository = sourceRepository;
        this.runtimeService = runtimeService;
    }

    public List<CollectTask> list() {
        return repository.findAll();
    }

    public List<CollectTaskResponse> list(int page, int size) {
        return repository.findAll(PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(CollectTaskResponse::from)
                .toList();
    }

    public CollectTask getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("采集任务不存在: " + id));
    }

    public CollectTaskResponse getResponseById(Long id) {
        return CollectTaskResponse.from(getById(id));
    }

    public CollectTask create(CollectTask task) {
        if (task.getSourceId() == null) {
            throw new IllegalArgumentException("关联数据源不能为空");
        }
        if (!sourceRepository.existsById(task.getSourceId())) {
            throw new ResourceNotFoundException("关联数据源不存在: " + task.getSourceId());
        }
        task.setId(null);
        if (task.getStatus() == null) task.setStatus("IDLE");
        if (task.getRetryCount() == null) task.setRetryCount(0);
        if (task.getMaxRetries() == null) task.setMaxRetries(3);
        normalizeAndValidate(task);
        CollectTask saved = repository.save(task);
        synchronizeRuntime(saved);
        return saved;
    }

    public CollectTaskResponse create(CollectTaskRequest request) {
        CollectTask task = new CollectTask();
        applyRequest(task, request);
        return CollectTaskResponse.from(create(task));
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
        normalizeAndValidate(exist);
        CollectTask saved = repository.save(exist);
        synchronizeRuntime(saved);
        return saved;
    }

    public CollectTaskResponse update(Long id, CollectTaskRequest request) {
        CollectTask existing = getById(id);
        if (request.getSourceId() != null) {
            if (!sourceRepository.existsById(request.getSourceId())) {
                throw new ResourceNotFoundException("关联数据源不存在: " + request.getSourceId());
            }
            existing.setSourceId(request.getSourceId());
        }
        applyRequest(existing, request);
        normalizeAndValidate(existing);
        CollectTask saved = repository.save(existing);
        synchronizeRuntime(saved);
        return CollectTaskResponse.from(saved);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("采集任务不存在: " + id);
        }
        runtimeService.cancel(id);
        repository.deleteById(id);
    }

    public List<CollectTask> listBySourceId(Long sourceId) {
        return repository.findBySourceId(sourceId);
    }

    public List<CollectTaskResponse> listBySourceId(Long sourceId, int page, int size) {
        return repository.findBySourceId(sourceId, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(CollectTaskResponse::from)
                .toList();
    }

    private void applyRequest(CollectTask task, CollectTaskRequest request) {
        if (request.getSourceId() != null) task.setSourceId(request.getSourceId());
        if (request.getTaskName() != null) task.setTaskName(request.getTaskName().trim());
        if (request.getCronExpression() != null) task.setCronExpression(request.getCronExpression().trim());
        if (request.getStatus() != null) task.setStatus(request.normalizedStatus());
        if (request.getMaxRetries() != null) task.setMaxRetries(request.getMaxRetries());
    }

    private void normalizeAndValidate(CollectTask task) {
        if (task.getStatus() == null || !CollectTaskRequest.isSupportedStatus(task.getStatus())) {
            throw new IllegalArgumentException("任务状态只能是 IDLE、SCHEDULED、RUNNING、PAUSED、FAILED 或 ERROR");
        }
        task.setStatus(task.getStatus().trim().toUpperCase(java.util.Locale.ROOT));

        if (task.getCronExpression() != null && !task.getCronExpression().isBlank()
                && !CronExpression.isValidExpression(task.getCronExpression())) {
            throw new IllegalArgumentException("Cron 表达式不合法");
        }
        if ("SCHEDULED".equals(task.getStatus())
                && (task.getCronExpression() == null || task.getCronExpression().isBlank())) {
            throw new IllegalArgumentException("定时任务必须提供 Cron 表达式");
        }
        if (task.getMaxRetries() == null || task.getMaxRetries() < 0 || task.getMaxRetries() > 3) {
            throw new IllegalArgumentException("最大重试次数必须在 0 到 3 之间");
        }
    }

    private void synchronizeRuntime(CollectTask task) {
        switch (task.getStatus()) {
            case "SCHEDULED":
                runtimeService.registerOrUpdate(task);
                break;
            case "PAUSED":
                runtimeService.pause(task.getId());
                break;
            case "RUNNING":
                runtimeService.run(task.getId());
                break;
            default:
                runtimeService.cancel(task.getId());
                break;
        }
    }
}
