package com.career.platform.position.service;

import com.career.platform.common.PageResponse;
import com.career.platform.common.ResourceNotFoundException;
import com.career.platform.common.security.PublicRecruitmentScope;
import com.career.platform.common.security.PublicRecruitmentScopePolicy;
import com.career.platform.position.dto.PositionFilter;
import com.career.platform.position.dto.PositionResponse;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.PositionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.career.platform.position.repository.PositionSpecifications;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PositionService {
    private static final Set<String> SORT_FIELDS = Set.of("publishDate", "createTime", "salaryMin", "salaryMax", "title");
    private final PositionRepository repository;
    private final PublicRecruitmentScopePolicy scopePolicy;

    @Autowired
    public PositionService(PositionRepository repository, PublicRecruitmentScopePolicy scopePolicy) {
        this.repository = repository;
        this.scopePolicy = scopePolicy;
    }

    /** Kept for lightweight tests and adapters compiled against the original constructor. */
    public PositionService(PositionRepository repository) {
        this(repository, new PublicRecruitmentScopePolicy());
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

    /**
     * Shared recruitment records are intentionally not filtered by the caller's college or id.
     * The returned policy is exposed separately so Open API clients can display the same boundary.
     */
    public PageResponse<PositionResponse> searchPublicRecruitment(PositionFilter filter) {
        scopePolicy.resolve();
        return search(filter);
    }

    public PositionResponse get(Long id) {
        JobPosition position = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("岗位不存在: " + id));
        return new PositionResponse(position, true);
    }

    public PositionResponse getPublicRecruitment(Long id) {
        scopePolicy.resolve();
        return get(id);
    }

    public List<PositionResponse> latest(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit 必须在 1 到 100 之间");
        }
        return repository.findLatest(PageRequest.of(0, limit)).stream()
                .map(position -> new PositionResponse(position, false))
                .collect(Collectors.toList());
    }

    public List<PositionResponse> latestPublicRecruitment(int limit) {
        scopePolicy.resolve();
        return latest(limit);
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

    public List<String> suggestPublicTitles(String keyword, int limit) {
        scopePolicy.resolve();
        return suggest(keyword, limit);
    }

    public List<PositionResponse> comparePublicRecruitment(Collection<Long> ids) {
        scopePolicy.resolve();
        if (ids == null || ids.size() < 2 || ids.size() > 3) {
            throw new IllegalArgumentException("同类岗位对比需要 2 到 3 个岗位");
        }
        List<Long> orderedIds = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (orderedIds.size() != ids.size()) {
            throw new IllegalArgumentException("岗位对比 ID 不能为空或重复");
        }
        Map<Long, JobPosition> byId = new LinkedHashMap<>();
        repository.findByIdIn(orderedIds).forEach(position -> byId.put(position.getId(), position));
        if (byId.size() != orderedIds.size()) {
            throw new ResourceNotFoundException("存在未找到的岗位");
        }
        return orderedIds.stream()
                .map(byId::get)
                .map(position -> new PositionResponse(position, true))
                .collect(Collectors.toList());
    }

    public PublicRecruitmentScope publicRecruitmentScope() {
        return scopePolicy.resolve();
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
        String sortDirection = filter.getSortDirection() == null ? "desc" : filter.getSortDirection().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("asc", "desc").contains(sortDirection)) {
            throw new IllegalArgumentException("sortDirection 仅支持 asc 或 desc");
        }
        filter.setSortDirection(sortDirection);
    }
}
