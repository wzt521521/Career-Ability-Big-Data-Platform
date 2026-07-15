package com.career.platform.openapi.service.impl;

import com.career.platform.common.PageResponse;
import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.openapi.dto.ApiKeyResponse;
import com.career.platform.openapi.dto.CreateApiKeyRequest;
import com.career.platform.openapi.dto.CreatedApiKeyResponse;
import com.career.platform.openapi.entity.ApiKey;
import com.career.platform.openapi.repository.ApiKeyRepository;
import com.career.platform.openapi.service.ApiKeyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyServiceImpl(
            ApiKeyRepository apiKeyRepository,
            CurrentUserProvider currentUserProvider) {
        this.apiKeyRepository = apiKeyRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApiKeyResponse> list(String appName, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Long userId = currentUserProvider.requireCurrentUser().getId();
        Page<ApiKey> apiKeys = apiKeyRepository.findByUserIdAndAppNameContainingIgnoreCase(
                userId,
                appName == null ? "" : appName.trim(),
                pageRequest);
        List<ApiKeyResponse> content = apiKeys.getContent().stream()
                .map(ApiKeyResponse::from)
                .collect(Collectors.toList());
        return PageResponse.from(apiKeys, content);
    }

    @Override
    @Transactional
    public CreatedApiKeyResponse create(CreateApiKeyRequest request) {
        if (request.getExpireTime() != null && !request.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Expiration time must be in the future");
        }
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawApiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(currentUserProvider.requireCurrentUser().getId());
        apiKey.setApiKeyHash(hash(rawApiKey));
        apiKey.setAppName(request.getAppName());
        apiKey.setStatus(1);
        apiKey.setRateLimit(request.getRateLimit() == null ? 100 : request.getRateLimit());
        apiKey.setTotalCalls(0L);
        apiKey.setExpireTime(request.getExpireTime());
        return new CreatedApiKeyResponse(apiKeyRepository.save(apiKey), rawApiKey);
    }

    @Override
    @Transactional
    public ApiKeyResponse updateStatus(Long id, int status) {
        ApiKey apiKey = requireApiKey(id);
        apiKey.setStatus(status);
        return ApiKeyResponse.from(apiKeyRepository.save(apiKey));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        apiKeyRepository.delete(requireApiKey(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiKey authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "X-API-Key header is required");
        }
        ApiKey apiKey = apiKeyRepository.findByApiKeyHash(hash(rawApiKey))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid API key"));
        if (!Integer.valueOf(1).equals(apiKey.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "API key is disabled");
        }
        if (apiKey.getExpireTime() != null && !apiKey.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "API key has expired");
        }
        return apiKey;
    }

    private ApiKey requireApiKey(Long id) {
        Long userId = currentUserProvider.requireCurrentUser().getId();
        return apiKeyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API key not found"));
    }

    private String hash(String rawApiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawApiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
