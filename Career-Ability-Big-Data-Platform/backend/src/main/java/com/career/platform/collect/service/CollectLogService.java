package com.career.platform.collect.service;

import com.career.platform.collect.dto.CollectLogResponse;
import com.career.platform.collect.entity.CollectLog;
import com.career.platform.collect.repository.CollectLogRepository;
import com.career.platform.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class CollectLogService {

    private final CollectLogRepository repository;

    public CollectLogService(CollectLogRepository repository) {
        this.repository = repository;
    }

    public List<CollectLog> list() {
        return repository.findAll();
    }

    public List<CollectLogResponse> list(int page, int size) {
        return repository.findAll(PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "startTime")))
                .stream()
                .map(CollectLogResponse::from)
                .toList();
    }

    public CollectLog getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("执行日志不存在: " + id));
    }

    public List<CollectLog> listByTaskId(Long taskId) {
        return repository.findByTaskIdOrderByStartTimeDesc(taskId);
    }

    public List<CollectLogResponse> listByTaskId(Long taskId, int page, int size) {
        return repository.findByTaskIdOrderByStartTimeDesc(taskId,
                        PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "startTime")))
                .stream()
                .map(CollectLogResponse::from)
                .toList();
    }

    public CollectLogResponse getResponseById(Long id) {
        return CollectLogResponse.from(getById(id));
    }
}
