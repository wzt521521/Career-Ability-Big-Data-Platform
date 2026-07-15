package com.career.platform.openapi.repository;

import com.career.platform.openapi.entity.ApiCallLog;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApiCallLogRepository extends JpaRepository<ApiCallLog, Long> {
    Page<ApiCallLog> findByApiPathContainingIgnoreCase(String apiPath, Pageable pageable);

    Page<ApiCallLog> findByApiKeyIdInAndApiPathContainingIgnoreCase(
            Collection<Long> apiKeyIds, String apiPath, Pageable pageable);

    long countByStatusCodeBetween(int minimum, int maximum);

    long countByApiKeyIdIn(Collection<Long> apiKeyIds);

    long countByApiKeyIdInAndStatusCodeBetween(Collection<Long> apiKeyIds, int minimum, int maximum);

    @Query("select avg(log.duration) from ApiCallLog log")
    Double averageDuration();

    @Query("select avg(log.duration) from ApiCallLog log where log.apiKeyId in :apiKeyIds")
    Double averageDurationByApiKeyIdIn(@org.springframework.data.repository.query.Param("apiKeyIds") Collection<Long> apiKeyIds);
}
