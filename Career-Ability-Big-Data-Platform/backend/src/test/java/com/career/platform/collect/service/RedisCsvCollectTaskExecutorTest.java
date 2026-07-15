package com.career.platform.collect.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.career.platform.collect.entity.CollectSource;
import com.career.platform.collect.entity.CollectTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisCsvCollectTaskExecutorTest {

    @TempDir
    Path dataRoot;

    @Test
    void convertsSupportedCsvRowsToThePipelineRawQueue() throws Exception {
        Path csv = dataRoot.resolve("jobs.csv");
        Files.writeString(csv, "jobId,title,companyName,salaryMin,salaryMax,city,skills,sourceUrl\n"
                + "one,Java Engineer,Acme,15,25,Shanghai,Java|Spring,https://example.test/one\n"
                + "two,,Ignored,10,20,Beijing,Java,https://example.test/two\n"
                + "three,Data Engineer,Data Co,20,35,Shenzhen,Python|Spark,https://example.test/three\n");
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        when(redisTemplate.<Long>execute(
                any(RedisScript.class), eq(List.of("raw", "dedupe")), any(), any())).thenReturn(1L);

        RedisCsvCollectTaskExecutor executor = new RedisCsvCollectTaskExecutor(
                redisTemplate, new ObjectMapper(), "raw", "dedupe", dataRoot.toString());
        CollectExecutionResult result = executor.execute(task(), source(csv));

        assertEquals(3, result.getTotalCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertEquals("jobs.csv", result.getFileName());
        verify(redisTemplate, times(2)).execute(
                any(RedisScript.class), eq(List.of("raw", "dedupe")), any(), any());
    }

    @Test
    void rejectsUnsupportedOrEscapingSourcesWithoutRetry() {
        CollectSource source = source(dataRoot.resolve("jobs.json"));
        source.setSourceType("URL");
        RedisCsvCollectTaskExecutor executor = new RedisCsvCollectTaskExecutor(
                org.mockito.Mockito.mock(StringRedisTemplate.class), new ObjectMapper(),
                "raw", "dedupe", dataRoot.toString());

        NonRetryableCollectTaskException error = assertThrows(
                NonRetryableCollectTaskException.class, () -> executor.execute(task(), source));

        assertTrue(error.getMessage().contains("FILE"));
    }

    private CollectTask task() {
        CollectTask task = new CollectTask();
        task.setId(1L);
        task.setSourceId(7L);
        task.setTaskName("sample");
        return task;
    }

    private CollectSource source(Path path) {
        CollectSource source = new CollectSource();
        source.setId(7L);
        source.setSourceType("FILE");
        source.setStatus(1);
        source.setFilePath(path.toString());
        return source;
    }
}
