package com.career.platform.collect.service;

import com.career.platform.collect.dto.CollectLogResponse;
import com.career.platform.collect.dto.CollectTaskResponse;
import com.career.platform.collect.entity.CollectLog;
import com.career.platform.collect.entity.CollectSource;
import com.career.platform.collect.entity.CollectTask;
import com.career.platform.collect.repository.CollectLogRepository;
import com.career.platform.collect.repository.CollectSourceRepository;
import com.career.platform.collect.repository.CollectTaskRepository;
import com.career.platform.common.ResourceNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.web.client.ResourceAccessException;

/**
 * Owns the in-process runtime for collection tasks. Configuration remains in
 * {@code collect_task}; this service owns only active schedules and attempts.
 */
@Service
public class CollectTaskRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(CollectTaskRuntimeService.class);
    private static final int MAX_SUPPORTED_RETRIES = 3;
    private static final int[] RETRY_DELAYS_MINUTES = {1, 5, 15};

    private final CollectTaskRepository taskRepository;
    private final CollectSourceRepository sourceRepository;
    private final CollectLogRepository logRepository;
    private final CollectTaskExecutor executor;
    private final TaskScheduler scheduler;
    private final boolean schedulingEnabled;

    private final Map<Long, ScheduledFuture<?>> cronFutures = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> retryFutures = new ConcurrentHashMap<>();
    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, ExecutionContext> activeExecutions = new ConcurrentHashMap<>();

    public CollectTaskRuntimeService(
            CollectTaskRepository taskRepository,
            CollectSourceRepository sourceRepository,
            CollectLogRepository logRepository,
            CollectTaskExecutor executor,
            @Qualifier("collectTaskScheduler") TaskScheduler scheduler,
            @Value("${spring.task.scheduling.enabled:true}") boolean schedulingEnabled) {
        this.taskRepository = taskRepository;
        this.sourceRepository = sourceRepository;
        this.logRepository = logRepository;
        this.executor = executor;
        this.scheduler = scheduler;
        this.schedulingEnabled = schedulingEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreScheduledTasks() {
        if (!schedulingEnabled) {
            return;
        }
        recoverInterruptedTasks();
        taskRepository.findByStatus("SCHEDULED").forEach(this::registerOrUpdate);
    }

    /** Schedule or reschedule an enabled task after its configuration is saved. */
    public void registerOrUpdate(CollectTask task) {
        if (task.getId() == null) {
            return;
        }
        cancelFuture(cronFutures, task.getId());
        if (!"SCHEDULED".equals(normalizeStatus(task.getStatus()))) {
            return;
        }
        if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
            task.setStatus("ERROR");
            task.setNextRunTime(null);
            saveTask(task);
            return;
        }
        if (!CronExpression.isValidExpression(task.getCronExpression())) {
            task.setStatus("ERROR");
            task.setNextRunTime(null);
            saveTask(task);
            return;
        }

        task.setNextRunTime(nextRun(task.getCronExpression()));
        saveTask(task);
        if (!schedulingEnabled) {
            return;
        }
        ScheduledFuture<?> future = scheduler.schedule(
                () -> runFromSchedule(task.getId()), new CronTrigger(task.getCronExpression()));
        if (future != null) {
            cronFutures.put(task.getId(), future);
        }
    }

    /** Cancel all future cron and retry work for a task before deletion or pause. */
    public void cancel(Long taskId) {
        cancelFuture(cronFutures, taskId);
        cancelFuture(retryFutures, taskId);
        ExecutionContext context = activeExecutions.get(taskId);
        if (context != null) {
            context.cancelled = true;
            if (!context.executing) {
                finishCancelled(context);
            }
        }
    }

    /** Start one manual execution without changing the task's recurring configuration. */
    public CollectTaskResponse run(Long id) {
        CollectTask task = getTask(id);
        String status = normalizeStatus(task.getStatus());
        if ("PAUSED".equals(status)) {
            throw new IllegalStateException("采集任务已暂停，请先恢复后再运行");
        }
        startExecution(task, "MANUAL", "SCHEDULED".equals(status));
        return CollectTaskResponse.from(getTask(id));
    }

    /** Stop future work. An already started source import is allowed to finish safely. */
    public CollectTaskResponse pause(Long id) {
        CollectTask task = getTask(id);
        cancelFuture(cronFutures, id);
        cancelFuture(retryFutures, id);
        task.setStatus("PAUSED");
        task.setNextRunTime(null);
        saveTask(task);
        ExecutionContext context = activeExecutions.get(id);
        if (context != null) {
            context.pauseRequested = true;
            if (!context.executing) {
                finishPaused(context);
            }
        }
        return CollectTaskResponse.from(task);
    }

    /** Re-enable an existing paused task; a cron task is registered again. */
    public CollectTaskResponse resume(Long id) {
        CollectTask task = getTask(id);
        if (!"PAUSED".equals(normalizeStatus(task.getStatus()))) {
            throw new IllegalStateException("只有已暂停的采集任务可以恢复");
        }
        if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
            task.setStatus("IDLE");
            task.setNextRunTime(null);
            saveTask(task);
        } else {
            task.setStatus("SCHEDULED");
            registerOrUpdate(task);
        }
        return CollectTaskResponse.from(task);
    }

    /** Compatibility helper for the task-scoped log endpoint. */
    public List<CollectLogResponse> listLogs(Long id, int page, int size) {
        getTask(id);
        return logRepository.findByTaskIdOrderByStartTimeDesc(
                        id, org.springframework.data.domain.PageRequest.of(
                                page - 1, size,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "startTime")))
                .stream()
                .map(CollectLogResponse::from)
                .toList();
    }

    private void runFromSchedule(Long id) {
        taskRepository.findById(id).ifPresent(task -> {
            if ("SCHEDULED".equals(normalizeStatus(task.getStatus()))) {
                startExecution(task, "CRON", true);
            }
        });
    }

    private void startExecution(CollectTask task, String trigger, boolean resumeSchedule) {
        Long taskId = task.getId();
        if (!runningTaskIds.add(taskId)) {
            log.info("Collection task {} skipped because another execution is active", taskId);
            return;
        }

        cancelFuture(retryFutures, taskId);
        task.setStatus("RUNNING");
        task.setLastRunTime(LocalDateTime.now());
        task.setNextRunTime(null);
        task.setRetryCount(0);
        saveTask(task);

        CollectLog executionLog = new CollectLog();
        executionLog.setTaskId(taskId);
        executionLog.setFileName(trigger);
        executionLog.setTotalCount(0);
        executionLog.setSuccessCount(0);
        executionLog.setFailCount(0);
        executionLog.setStartTime(LocalDateTime.now());
        executionLog = saveLog(executionLog);

        ExecutionContext context = new ExecutionContext(taskId, executionLog, resumeSchedule);
        activeExecutions.put(taskId, context);
        try {
            scheduler.schedule(() -> executeAttempt(context), new Date());
        } catch (RuntimeException error) {
            finishFailure(context, error);
        }
    }

    private void executeAttempt(ExecutionContext context) {
        synchronized (context) {
            if (context.closed) {
                return;
            }
            context.executing = true;
        }
        try {
            if (context.cancelled) {
                finishCancelled(context);
                return;
            }
            if (context.pauseRequested || isPaused(context.taskId)) {
                finishPaused(context);
                return;
            }
            CollectTask task = getTask(context.taskId);
            CollectSource source = sourceRepository.findById(task.getSourceId())
                    .orElseThrow(() -> new NonRetryableCollectTaskException(
                            "关联数据源不存在: " + task.getSourceId()));
            context.executionLog.setFileName(source.getFilePath());
            CollectExecutionResult result = executor.execute(task, source);
            finishSuccess(context, result);
        } catch (Throwable error) {
            finishFailure(context, error);
        } finally {
            context.executing = false;
            if (!context.closed && context.cancelled) {
                finishCancelled(context);
            } else if (!context.closed && context.pauseRequested) {
                finishPaused(context);
            }
        }
    }

    private void finishSuccess(ExecutionContext context, CollectExecutionResult result) {
        CollectTask task = findTask(context.taskId);
        if (task == null) {
            clearExecution(context);
            return;
        }
        if (context.cancelled) {
            finishCancelled(context);
            return;
        }
        CollectLog executionLog = context.executionLog;
        executionLog.setFileName(result.getFileName());
        executionLog.setTotalCount(result.getTotalCount());
        executionLog.setSuccessCount(result.getSuccessCount());
        executionLog.setFailCount(result.getFailCount());
        executionLog.setErrorMsg(null);
        executionLog.setEndTime(LocalDateTime.now());
        saveLog(executionLog);

        if (context.pauseRequested || "PAUSED".equals(normalizeStatus(task.getStatus()))) {
            clearExecution(context);
            return;
        }
        if (context.pauseRequested || isPaused(context.taskId)) {
            clearExecution(context);
            return;
        }
        task.setRetryCount(0);
        if (context.resumeSchedule && hasValidCron(task)) {
            task.setStatus("SCHEDULED");
            task.setNextRunTime(nextRun(task.getCronExpression()));
            saveTask(task);
            ensureCronRegistration(task);
        } else {
            task.setStatus("IDLE");
            task.setNextRunTime(null);
            saveTask(task);
        }
        clearExecution(context);
    }

    private void finishFailure(ExecutionContext context, Throwable error) {
        CollectTask task = findTask(context.taskId);
        if (task == null) {
            clearExecution(context);
            return;
        }
        if (context.cancelled) {
            finishCancelled(context);
            return;
        }
        if (context.pauseRequested || "PAUSED".equals(normalizeStatus(task.getStatus()))) {
            finishPaused(context);
            return;
        }

        String message = summarize(error);
        int retries = safeRetryCount(task);
        int maxRetries = Math.min(safeMaxRetries(task), MAX_SUPPORTED_RETRIES);
        if (isRetryable(error) && retries < maxRetries) {
            if (context.pauseRequested || isPaused(context.taskId)) {
                finishPaused(context);
                return;
            }
            int nextRetry = retries + 1;
            LocalDateTime retryAt = LocalDateTime.now().plusMinutes(RETRY_DELAYS_MINUTES[nextRetry - 1]);
            task.setRetryCount(nextRetry);
            task.setStatus("RUNNING");
            task.setNextRunTime(retryAt);
            saveTask(task);
            context.executionLog.setErrorMsg("第 " + nextRetry + "/" + maxRetries
                    + " 次重试将在 " + retryAt + " 执行：" + message);
            saveLog(context.executionLog);
            scheduleRetry(context, retryAt);
            return;
        }

        if (context.pauseRequested || isPaused(context.taskId)) {
            finishPaused(context);
            return;
        }
        task.setStatus(isRetryable(error) ? "FAILED" : "ERROR");
        task.setNextRunTime(null);
        saveTask(task);
        cancelFuture(cronFutures, context.taskId);
        context.executionLog.setFailCount(Math.max(1, context.executionLog.getFailCount()));
        context.executionLog.setErrorMsg(finalFailureMessage(error, retries, maxRetries));
        context.executionLog.setEndTime(LocalDateTime.now());
        saveLog(context.executionLog);
        clearExecution(context);
    }

    private void scheduleRetry(ExecutionContext context, LocalDateTime retryAt) {
        try {
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> executeAttempt(context), Date.from(retryAt.atZone(ZoneId.systemDefault()).toInstant()));
            if (future != null) {
                retryFutures.put(context.taskId, future);
            }
        } catch (RuntimeException schedulingError) {
            finishFailure(context, schedulingError);
        }
    }

    private void finishPaused(ExecutionContext context) {
        if (!markCompleted(context)) {
            return;
        }
        CollectTask task = findTask(context.taskId);
        if (task != null && "PAUSED".equals(normalizeStatus(task.getStatus()))) {
            context.executionLog.setErrorMsg("执行已暂停，未继续重试");
            context.executionLog.setEndTime(LocalDateTime.now());
            saveLog(context.executionLog);
        }
        clearExecution(context);
    }

    private void finishCancelled(ExecutionContext context) {
        if (!markCompleted(context)) {
            return;
        }
        if (findTask(context.taskId) != null) {
            context.executionLog.setErrorMsg("执行已取消");
            context.executionLog.setEndTime(LocalDateTime.now());
            saveLog(context.executionLog);
        }
        clearExecution(context);
    }

    private void ensureCronRegistration(CollectTask task) {
        if (!cronFutures.containsKey(task.getId()) && schedulingEnabled) {
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> runFromSchedule(task.getId()), new CronTrigger(task.getCronExpression()));
            if (future != null) {
                cronFutures.put(task.getId(), future);
            }
        }
    }

    private void recoverInterruptedTasks() {
        taskRepository.findByStatus("RUNNING").forEach(task -> {
            task.setStatus("ERROR");
            task.setNextRunTime(null);
            saveTask(task);
        });
    }

    private boolean isPaused(Long taskId) {
        CollectTask task = findTask(taskId);
        return task != null && "PAUSED".equals(normalizeStatus(task.getStatus()));
    }

    private boolean hasValidCron(CollectTask task) {
        return task.getCronExpression() != null
                && !task.getCronExpression().isBlank()
                && CronExpression.isValidExpression(task.getCronExpression());
    }

    private LocalDateTime nextRun(String cronExpression) {
        return CronExpression.parse(cronExpression).next(LocalDateTime.now());
    }

    private CollectTask getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("采集任务不存在: " + id));
    }

    private CollectTask findTask(Long id) {
        return taskRepository.findById(id).orElse(null);
    }

    private CollectTask saveTask(CollectTask task) {
        CollectTask saved = taskRepository.save(task);
        return saved == null ? task : saved;
    }

    private CollectLog saveLog(CollectLog collectLog) {
        CollectLog saved = logRepository.save(collectLog);
        return saved == null ? collectLog : saved;
    }

    private boolean markCompleted(ExecutionContext context) {
        synchronized (context) {
            if (context.closed) {
                return false;
            }
            context.closed = true;
            return true;
        }
    }

    private void clearExecution(ExecutionContext context) {
        markCompleted(context);
        runningTaskIds.remove(context.taskId);
        activeExecutions.remove(context.taskId, context);
        retryFutures.remove(context.taskId);
    }

    private void cancelFuture(Map<Long, ScheduledFuture<?>> futures, Long taskId) {
        ScheduledFuture<?> future = futures.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private int safeRetryCount(CollectTask task) {
        return task.getRetryCount() == null ? 0 : Math.max(0, task.getRetryCount());
    }

    private int safeMaxRetries(CollectTask task) {
        return task.getMaxRetries() == null ? MAX_SUPPORTED_RETRIES
                : Math.max(0, task.getMaxRetries());
    }

    private boolean isRetryable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof NonRetryableCollectTaskException
                    || current instanceof IllegalArgumentException
                    || current instanceof SecurityException
                    || current instanceof NoSuchFileException
                    || current instanceof AccessDeniedException
                    || current instanceof java.nio.charset.MalformedInputException
                    || current instanceof java.nio.charset.UnmappableCharacterException) {
                return false;
            }
            if (current instanceof RetryableCollectTaskException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof ResourceAccessException
                    || current instanceof DataAccessResourceFailureException
                    || current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String finalFailureMessage(Throwable error, int retries, int maxRetries) {
        if (isRetryable(error)) {
            return "采集失败，已完成 " + retries + "/" + maxRetries + " 次重试：" + summarize(error);
        }
        return "不可重试错误：" + summarize(error);
    }

    private String summarize(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private String normalizeStatus(String status) {
        return status == null ? "IDLE" : status.trim().toUpperCase(java.util.Locale.ROOT);
    }

    @PreDestroy
    public void shutdown() {
        cronFutures.values().forEach(future -> future.cancel(false));
        retryFutures.values().forEach(future -> future.cancel(false));
    }

    private static final class ExecutionContext {
        private final Long taskId;
        private final CollectLog executionLog;
        private final boolean resumeSchedule;
        private volatile boolean cancelled;
        private volatile boolean pauseRequested;
        private volatile boolean executing;
        private volatile boolean closed;

        private ExecutionContext(Long taskId, CollectLog executionLog, boolean resumeSchedule) {
            this.taskId = taskId;
            this.executionLog = executionLog;
            this.resumeSchedule = resumeSchedule;
        }
    }
}
