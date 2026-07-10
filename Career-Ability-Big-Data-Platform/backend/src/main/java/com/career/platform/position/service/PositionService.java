package com.career.platform.position.service;

import com.career.platform.common.PageResponse;
import com.career.platform.common.ResourceNotFoundException;
import com.career.platform.position.dto.PositionFilter;
import com.career.platform.position.dto.PositionResponse;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.PositionRepository;
import com.career.platform.position.repository.PositionSpecifications;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PositionService {
    private static final Set<String> SORT_FIELDS = Set.of("publishDate", "createTime", "salaryMin", "salaryMax", "title");
    private final PositionRepository repository;

    public PositionService(PositionRepository repository) {
        this.repository = repository;
    }

    public PageResponse<PositionResponse> search(PositionFilter filter) {
        validate(filter);
        Sort.Direction direction = "asc".equalsIgnoreCase(filter.getSortDirection())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, filter.getSortBy()).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getSize(), sort);
        Page<JobPosition> result = repository.findAll(PositionSpecifications.from(filter), pageable);
        List<PositionResponse> content = result.getContent().stream()
                .map(position -> new PositionResponse(position, false))
                .collect(Collectors.toList());
        return PageResponse.from(result, content);
    }

    public PositionResponse get(Long id) {
        JobPosition position = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("岗位不存在: " + id));
        return new PositionResponse(position, true);
    }

    public List<PositionResponse> latest(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit 必须在 1 到 100 之间");
        }
        return repository.findLatest(PageRequest.of(0, limit)).stream()
                .map(position -> new PositionResponse(position, false))
                .collect(Collectors.toList());
    }

    public List<String> suggest(String keyword, int limit) {
        if (keyword == null || keyword.trim().length() < 1) {
            return List.of();
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("limit 必须在 1 到 20 之间");
        }
        return repository.suggestTitles(keyword.trim(), PageRequest.of(0, limit));
    }

    private void validate(PositionFilter filter) {
        if (filter.getPage() < 1) {
            throw new IllegalArgumentException("page 必须大于等于 1");
        }
        if (filter.getSize() < 1 || filter.getSize() > 100) {
            throw new IllegalArgumentException("size 必须在 1 到 100 之间");
        }
        if (filter.getSalaryMin() != null && filter.getSalaryMin() < 0
                || filter.getSalaryMax() != null && filter.getSalaryMax() < 0) {
            throw new IllegalArgumentException("薪资不能为负数");
        }
        if (filter.getSalaryMin() != null && filter.getSalaryMax() != null
                && filter.getSalaryMin() > filter.getSalaryMax()) {
            throw new IllegalArgumentException("最低薪资不能高于最高薪资");
        }
        String sortBy = filter.getSortBy() == null ? "publishDate" : filter.getSortBy().trim();
        if (!SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("不支持的排序字段: " + sortBy.toLowerCase(Locale.ROOT));
        }
        filter.setSortBy(sortBy);
    }
}
