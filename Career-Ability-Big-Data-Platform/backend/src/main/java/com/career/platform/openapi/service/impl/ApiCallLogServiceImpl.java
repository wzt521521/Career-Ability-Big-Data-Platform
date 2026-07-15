package com.career.platform.openapi.service.impl;

import com.career.platform.common.PageResponse;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.openapi.dto.ApiCallLogResponse;
import com.career.platform.openapi.dto.ApiCallStatisticsResponse;
import com.career.platform.openapi.entity.ApiCallLog;
import com.career.platform.openapi.entity.ApiKey;
import com.career.platform.openapi.repository.ApiCallLogRepository;
import com.career.platform.openapi.repository.ApiKeyRepository;
import com.career.platform.openapi.service.ApiCallLogService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiCallLogServiceImpl implements ApiCallLogService {

    private final ApiCallLogRepository callLogRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final CurrentUserProvider currentUserProvider;

    public ApiCallLogServiceImpl(
            ApiCallLogRepository callLogRepository,
            ApiKeyRepository apiKeyRepository,
            CurrentUserProvider currentUserProvider) {
        this.callLogRepository = callLogRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            ApiKey apiKey,
            String path,
            String method,
            String parameterNames,
            String ip,
            long duration,
            int statusCode) {
        ApiCallLog callLog = new ApiCallLog();
        callLog.setApiKeyId(apiKey.getId());
        callLog.setApiPath(path);
        callLog.setMethod(method);
        callLog.setParams(parameterNames);
        callLog.setIp(ip);
        callLog.setDuration(duration);
        callLog.setStatusCode(statusCode);
        callLogRepository.save(callLog);
        apiKeyRepository.incrementTotalCalls(apiKey.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApiCallLogResponse> list(String path, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        List<Long> apiKeyIds = currentApiKeyIds();
        if (apiKeyIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageRequest), List.of());
        }
        Page<ApiCallLog> logs = callLogRepository.findByApiKeyIdInAndApiPathContainingIgnoreCase(
                apiKeyIds,
                path == null ? "" : path.trim(),
                pageRequest);
        List<ApiCallLogResponse> content = logs.getContent().stream()
                .map(ApiCallLogResponse::from)
                .collect(Collectors.toList());
        return PageResponse.from(logs, content);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiCallStatisticsResponse statistics() {
        List<Long> apiKeyIds = currentApiKeyIds();
        if (apiKeyIds.isEmpty()) {
            return new ApiCallStatisticsResponse(0L, 0L, 0L, 0.0);
        }
        long total = callLogRepository.countByApiKeyIdIn(apiKeyIds);
        long successful = callLogRepository.countByApiKeyIdInAndStatusCodeBetween(apiKeyIds, 200, 399);
        Double averageDuration = callLogRepository.averageDurationByApiKeyIdIn(apiKeyIds);
        return new ApiCallStatisticsResponse(
                total,
                successful,
                total - successful,
                averageDuration == null ? 0.0 : averageDuration);
    }

    private List<Long> currentApiKeyIds() {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        return apiKeyRepository.findByUserId(userId).stream()
                .map(ApiKey::getId)
                .collect(Collectors.toList());
    }
}
