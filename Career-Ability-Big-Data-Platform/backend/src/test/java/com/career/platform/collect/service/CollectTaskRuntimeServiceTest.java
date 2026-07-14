package com.career.platform.collect.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.career.platform.collect.entity.CollectLog;
import com.career.platform.collect.entity.CollectSource;
import com.career.platform.collect.entity.CollectTask;
import com.career.platform.collect.repository.CollectLogRepository;
import com.career.platform.collect.repository.CollectSourceRepository;
import com.career.platform.collect.repository.CollectTaskRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

@ExtendWith(MockitoExtension.class)
class CollectTaskRuntimeServiceTest {

    @Mock
    private CollectTaskRepository taskRepository;

    @Mock
    private CollectSourceRepository sourceRepository;

    @Mock
    private CollectLogRepository logRepository;

    @Mock
    private CollectTaskExecutor executor;

    @Mock
    private TaskScheduler scheduler;

    private CollectTaskRuntimeService runtimeService;
    private CollectTask task;
    private CollectSource source;

    @BeforeEach
    void setUp() {
        runtimeService = new CollectTaskRuntimeService(
                taskRepository, sourceRepository, logRepository, executor, scheduler, true);
        task = task("IDLE", 3);
        source = new CollectSource();
        source.setId(7L);
        source.setSourceType("FILE");
        source.setStatus(1);
        source.setFilePath("/data/sample.csv");

        lenient().when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        lenient().when(taskRepository.save(any(CollectTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(logRepository.save(any(CollectLog.class))).thenAnswer(invocation -> {
            CollectLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(10L);
            }
            return log;
        });
        lenient().when(sourceRepository.findById(7L)).thenReturn(Optional.of(source));
    }

    @Test
    void registersAndReplacesCronFutureForScheduledTask() {
        task.setStatus("SCHEDULED");
        task.setCronExpression("0 0 2 * * *");
        java.util.concurrent.ScheduledFuture<?> oldFuture = org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        java.util.concurrent.ScheduledFuture<?> newFuture = org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        doReturn(oldFuture, newFuture).when(scheduler)
                .schedule(any(Runnable.class), any(CronTrigger.class));

        runtimeService.registerOrUpdate(task);
        runtimeService.registerOrUpdate(task);

        verify(scheduler, times(2)).schedule(any(Runnable.class), any(CronTrigger.class));
        verify(oldFuture).cancel(false);
        assertNotNull(task.getNextRunTime());
    }

    @Test
    void rejectsConcurrentManualRunsForTheSameTask() {
        when(scheduler.schedule(any(Runnable.class), any(Date.class))).thenReturn(null);

        runtimeService.run(1L);
        runtimeService.run(1L);

        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Date.class));
        verify(logRepository, times(1)).save(any(CollectLog.class));
        assertEquals("RUNNING", task.getStatus());
    }

    @Test
    void retriesRetryableFailureAfterOneMinuteThenMarksTaskFailed() throws Exception {
        task.setMaxRetries(1);
        when(scheduler.schedule(any(Runnable.class), any(Date.class))).thenReturn(null);
        when(executor.execute(eq(task), eq(source)))
                .thenThrow(new RetryableCollectTaskException("Redis temporarily unavailable"));

        runtimeService.run(1L);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
        verify(scheduler).schedule(runnableCaptor.capture(), dateCaptor.capture());
        runnableCaptor.getValue().run();

        verify(scheduler, times(2)).schedule(runnableCaptor.capture(), dateCaptor.capture());
        assertEquals("RUNNING", task.getStatus());
        assertEquals(1, task.getRetryCount());
        assertNotNull(task.getNextRunTime());
        long retryDelaySeconds = Duration.between(LocalDateTime.now(), task.getNextRunTime()).getSeconds();
        assertTrue(retryDelaySeconds >= 59 && retryDelaySeconds <= 61);

        runnableCaptor.getAllValues().get(1).run();

        assertEquals("FAILED", task.getStatus());
        verify(logRepository, times(3)).save(any(CollectLog.class));
    }

    @Test
    void recordsNonRetryableFailureWithoutSchedulingAnotherAttempt() throws Exception {
        when(scheduler.schedule(any(Runnable.class), any(Date.class))).thenReturn(null);
        when(executor.execute(eq(task), eq(source)))
                .thenThrow(new NonRetryableCollectTaskException("CSV header is invalid"));

        runtimeService.run(1L);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(runnableCaptor.capture(), any(Date.class));
        runnableCaptor.getValue().run();

        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Date.class));
        assertEquals("ERROR", task.getStatus());
        assertEquals(0, task.getRetryCount());
    }

    @Test
    void pauseCancelsCronAndKeepsTaskPaused() {
        task.setStatus("SCHEDULED");
        task.setCronExpression("0 0 2 * * *");
        java.util.concurrent.ScheduledFuture<?> future = org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        runtimeService.registerOrUpdate(task);

        runtimeService.pause(1L);

        verify(future).cancel(false);
        assertEquals("PAUSED", task.getStatus());
        assertNull(task.getNextRunTime());
    }

    @Test
    void pauseClosesAnExecutionThatIsWaitingForRetry() throws Exception {
        task.setMaxRetries(1);
        when(scheduler.schedule(any(Runnable.class), any(Date.class))).thenReturn(null);
        when(executor.execute(eq(task), eq(source)))
                .thenThrow(new RetryableCollectTaskException("temporary Redis failure"));

        runtimeService.run(1L);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(runnableCaptor.capture(), any(Date.class));
        runnableCaptor.getValue().run();

        runtimeService.pause(1L);

        ArgumentCaptor<CollectLog> logCaptor = ArgumentCaptor.forClass(CollectLog.class);
        verify(logRepository, times(3)).save(logCaptor.capture());
        CollectLog completedLog = logCaptor.getAllValues().get(2);
        assertEquals("PAUSED", task.getStatus());
        assertNotNull(completedLog.getEndTime());
        assertTrue(completedLog.getErrorMsg().contains("暂停"));

        runtimeService.resume(1L);
        runtimeService.run(1L);
        verify(scheduler, times(3)).schedule(any(Runnable.class), any(Date.class));
    }

    @Test
    void cancelClosesAQueuedExecutionBeforeItStarts() {
        when(scheduler.schedule(any(Runnable.class), any(Date.class))).thenReturn(null);

        runtimeService.run(1L);
        runtimeService.cancel(1L);

        ArgumentCaptor<CollectLog> logCaptor = ArgumentCaptor.forClass(CollectLog.class);
        verify(logRepository, times(2)).save(logCaptor.capture());
        assertNotNull(logCaptor.getAllValues().get(1).getEndTime());
        assertTrue(logCaptor.getAllValues().get(1).getErrorMsg().contains("取消"));

        runtimeService.run(1L);
        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Date.class));
    }

    private CollectTask task(String status, int maxRetries) {
        CollectTask value = new CollectTask();
        value.setId(1L);
        value.setSourceId(7L);
        value.setTaskName("sample import");
        value.setStatus(status);
        value.setRetryCount(0);
        value.setMaxRetries(maxRetries);
        return value;
    }
}
