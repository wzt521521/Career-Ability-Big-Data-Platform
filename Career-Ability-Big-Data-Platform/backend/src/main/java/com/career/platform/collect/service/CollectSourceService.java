package com.career.platform.collect.service;

import com.career.platform.collect.dto.CollectSourceRequest;
import com.career.platform.collect.dto.CollectSourceResponse;
import com.career.platform.collect.entity.CollectSource;
import com.career.platform.collect.repository.CollectSourceRepository;
import com.career.platform.common.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class CollectSourceService {

    private final CollectSourceRepository repository;

    public CollectSourceService(CollectSourceRepository repository) {
        this.repository = repository;
    }

    public List<CollectSource> list() {
        return repository.findAll();
    }

    public List<CollectSourceResponse> list(int page, int size) {
        return repository.findAll(PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(CollectSourceResponse::from)
                .toList();
    }

    public CollectSource getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("数据源不存在: " + id));
    }

    public CollectSourceResponse getResponseById(Long id) {
        return CollectSourceResponse.from(getById(id));
    }

    public CollectSource create(CollectSource source) {
        source.setId(null);
        if (source.getSourceType() == null) source.setSourceType("FILE");
        else source.setSourceType(normalizeSourceType(source.getSourceType()));
        if (source.getImportFrequency() == null) source.setImportFrequency("MANUAL");
        else source.setImportFrequency(normalizeFrequency(source.getImportFrequency()));
        if (source.getStatus() == null) source.setStatus(1);
        validateSourcePath(source);
        return repository.save(source);
    }

    public CollectSourceResponse create(CollectSourceRequest request) {
        CollectSource source = new CollectSource();
        applyRequest(source, request);
        return CollectSourceResponse.from(create(source));
    }

    public CollectSource update(Long id, CollectSource source) {
        CollectSource exist = getById(id);
        if (source.getSourceName() != null) exist.setSourceName(source.getSourceName());
        if (source.getFilePath() != null) exist.setFilePath(source.getFilePath());
        if (source.getFieldMapping() != null) exist.setFieldMapping(source.getFieldMapping());
        if (source.getImportFrequency() != null) exist.setImportFrequency(normalizeFrequency(source.getImportFrequency()));
        if (source.getStatus() != null) exist.setStatus(source.getStatus());
        if (source.getDescription() != null) exist.setDescription(source.getDescription());
        if (source.getSourceType() != null) exist.setSourceType(normalizeSourceType(source.getSourceType()));
        validateSourcePath(exist);
        return repository.save(exist);
    }

    public CollectSourceResponse update(Long id, CollectSourceRequest request) {
        CollectSource existing = getById(id);
        applyRequest(existing, request);
        validateSourcePath(existing);
        return CollectSourceResponse.from(repository.save(existing));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("数据源不存在: " + id);
        }
        repository.deleteById(id);
    }

    private void applyRequest(CollectSource source, CollectSourceRequest request) {
        if (request.getSourceName() != null) source.setSourceName(request.getSourceName().trim());
        if (request.getSourceType() != null) source.setSourceType(normalizeSourceType(request.getSourceType()));
        if (request.getFilePath() != null) source.setFilePath(request.getFilePath().trim());
        if (request.getFieldMapping() != null) source.setFieldMapping(request.getFieldMapping());
        if (request.getImportFrequency() != null) {
            source.setImportFrequency(normalizeFrequency(request.getImportFrequency()));
        }
        if (request.getStatus() != null) source.setStatus(request.getStatus());
        if (request.getDescription() != null) source.setDescription(request.getDescription());
    }

    private void validateSourcePath(CollectSource source) {
        if (!CollectSourceRequest.isPathAllowed(source.getSourceType(), source.getFilePath())) {
            throw new IllegalArgumentException(
                    "FILE 数据源必须使用 /data 下的安全相对路径，URL 数据源必须使用无凭据的 HTTP(S) URL");
        }
    }

    private String normalizeSourceType(String sourceType) {
        return sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFrequency(String importFrequency) {
        return importFrequency.trim().toUpperCase(Locale.ROOT);
    }
}
