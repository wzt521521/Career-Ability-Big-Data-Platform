package com.career.platform.collect.service;

/** Result counters returned by one source collection execution. */
public final class CollectExecutionResult {

    private final String fileName;
    private final int totalCount;
    private final int successCount;
    private final int failCount;

    public CollectExecutionResult(String fileName, int totalCount, int successCount, int failCount) {
        this.fileName = fileName;
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failCount = failCount;
    }

    public String getFileName() {
        return fileName;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailCount() {
        return failCount;
    }
}
