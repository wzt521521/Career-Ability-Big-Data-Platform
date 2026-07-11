package com.career.platform.collect.service;

import com.career.platform.collect.entity.CollectSource;
import com.career.platform.collect.repository.CollectSourceRepository;
import com.career.platform.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CollectSourceService {

    private final CollectSourceRepository repository;

    public CollectSourceService(CollectSourceRepository repository) {
        this.repository = repository;
    }

    public List<CollectSource> list() {
        return repository.findAll();
    }

    public CollectSource getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("数据源不存在: " + id));
    }

    public CollectSource create(CollectSource source) {
        source.setId(null);
        if (source.getSourceType() == null) source.setSourceType("FILE");
        if (source.getImportFrequency() == null) source.setImportFrequency("manual");
        if (source.getStatus() == null) source.setStatus(1);
        return repository.save(source);
    }

    public CollectSource update(Long id, CollectSource source) {
        CollectSource exist = getById(id);
        if (source.getSourceName() != null) exist.setSourceName(source.getSourceName());
        if (source.getSourceType() != null) exist.setSourceType(source.getSourceType());
        if (source.getFilePath() != null) exist.setFilePath(source.getFilePath());
        if (source.getFieldMapping() != null) exist.setFieldMapping(source.getFieldMapping());
        if (source.getImportFrequency() != null) exist.setImportFrequency(source.getImportFrequency());
        if (source.getStatus() != null) exist.setStatus(source.getStatus());
        if (source.getDescription() != null) exist.setDescription(source.getDescription());
        return repository.save(exist);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("数据源不存在: " + id);
        }
        repository.deleteById(id);
    }
}
