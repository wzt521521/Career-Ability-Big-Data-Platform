package com.career.platform.collect.service;

import com.career.platform.collect.entity.CollectSource;
import com.career.platform.collect.entity.CollectTask;

/** Performs the source-specific work for a scheduled collection task. */
public interface CollectTaskExecutor {

    CollectExecutionResult execute(CollectTask task, CollectSource source) throws Exception;
}
