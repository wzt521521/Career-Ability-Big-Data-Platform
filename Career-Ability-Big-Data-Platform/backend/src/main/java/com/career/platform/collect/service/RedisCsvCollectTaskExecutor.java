package com.career.platform.collect.service;

import com.career.platform.collect.entity.CollectSource;
import com.career.platform.collect.entity.CollectTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * The v1 collection adapter for the supported offline data source: CSV files
 * in the mounted data directory. It emits the same raw-job shape consumed by
 * {@code data-pipeline/etl_clean.py} and atomically preserves source identity.
 */
@Component
public class RedisCsvCollectTaskExecutor implements CollectTaskExecutor {

    private static final String ENQUEUE_SCRIPT = ""
            + "if redis.call('SADD', KEYS[2], ARGV[1]) == 1 then "
            + "redis.call('LPUSH', KEYS[1], ARGV[2]); return 1; "
            + "end; return 0;";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String rawQueue;
    private final String rawDedupeSet;
    private final Path dataRoot;
    private final DefaultRedisScript<Long> enqueueScript;

    public RedisCsvCollectTaskExecutor(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.collect.queue.raw:queue:raw-job-data}") String rawQueue,
            @Value("${app.collect.queue.raw-dedupe:dedupe:raw-job-data}") String rawDedupeSet,
            @Value("${app.collect.data-root:/data}") String dataRoot) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rawQueue = rawQueue;
        this.rawDedupeSet = rawDedupeSet;
        this.dataRoot = Paths.get(dataRoot).toAbsolutePath().normalize();
        this.enqueueScript = new DefaultRedisScript<>(ENQUEUE_SCRIPT, Long.class);
    }

    @Override
    public CollectExecutionResult execute(CollectTask task, CollectSource source) {
        Path sourceFile = validateSource(source);
        int total = 0;
        int success = 0;
        int failed = 0;

        try (BufferedReader reader = Files.newBufferedReader(sourceFile, StandardCharsets.UTF_8)) {
            List<String> headers = readRecord(reader);
            if (headers == null || headers.isEmpty()) {
                throw new NonRetryableCollectTaskException("CSV 文件缺少表头: " + sourceFile.getFileName());
            }
            Map<String, Integer> headerIndexes = headerIndexes(headers);
            if (firstHeader(headerIndexes, "title", "job title", "job_title", "position") == null
                    || firstHeader(headerIndexes, "companyname", "company", "company name", "company_name") == null) {
                throw new NonRetryableCollectTaskException("CSV 必须包含职位名称和公司名称列");
            }

            List<String> record;
            while ((record = readRecord(reader)) != null) {
                if (record.stream().allMatch(String::isBlank)) {
                    continue;
                }
                total++;
                try {
                    Map<String, String> row = rowFor(headers, record);
                    Map<String, Object> job = toRawJob(task, row, total);
                    if (isBlank((String) job.get("title"))
                            || isBlank((String) ((Map<?, ?>) job.get("company")).get("name"))) {
                        failed++;
                        continue;
                    }
                    enqueue(job);
                    success++;
                } catch (RetryableCollectTaskException error) {
                    throw error;
                } catch (RuntimeException error) {
                    failed++;
                }
            }
        } catch (NonRetryableCollectTaskException | RetryableCollectTaskException error) {
            throw error;
        } catch (java.nio.charset.MalformedInputException error) {
            throw new NonRetryableCollectTaskException("CSV 文件不是有效的 UTF-8 文本", error);
        } catch (IOException error) {
            throw new RetryableCollectTaskException("读取采集文件失败: " + sourceFile.getFileName(), error);
        }
        return new CollectExecutionResult(sourceFile.getFileName().toString(), total, success, failed);
    }

    private Path validateSource(CollectSource source) {
        if (source.getStatus() != null && source.getStatus() != 1) {
            throw new NonRetryableCollectTaskException("关联数据源已禁用");
        }
        if (!"FILE".equalsIgnoreCase(source.getSourceType())) {
            throw new NonRetryableCollectTaskException("当前发行版本仅支持 FILE 类型采集源");
        }
        if (isBlank(source.getFilePath())) {
            throw new NonRetryableCollectTaskException("FILE 数据源缺少文件路径");
        }
        Path configured = Paths.get(source.getFilePath()).toAbsolutePath().normalize();
        if (!configured.startsWith(dataRoot)) {
            throw new NonRetryableCollectTaskException("采集文件必须位于受控目录: " + dataRoot);
        }
        if (!configured.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new NonRetryableCollectTaskException("当前发行版本仅支持 CSV 文件采集");
        }
        try {
            Path realRoot = dataRoot.toRealPath();
            Path realFile = configured.toRealPath();
            if (!realFile.startsWith(realRoot) || !Files.isRegularFile(realFile)) {
                throw new NonRetryableCollectTaskException("采集文件不是受控目录中的常规文件");
            }
            return realFile;
        } catch (java.nio.file.NoSuchFileException error) {
            throw new NonRetryableCollectTaskException("采集文件不存在: " + configured, error);
        } catch (IOException error) {
            throw new NonRetryableCollectTaskException("无法验证采集文件: " + configured, error);
        }
    }

    private void enqueue(Map<String, Object> job) {
        try {
            String sourceMd5 = sourceMd5(job);
            job.put("sourceMd5", sourceMd5);
            String payload = objectMapper.writeValueAsString(job);
            Long inserted = redisTemplate.execute(enqueueScript, List.of(rawQueue, rawDedupeSet), sourceMd5, payload);
            if (inserted == null) {
                throw new RetryableCollectTaskException("Redis 未返回入队结果");
            }
        } catch (JsonProcessingException error) {
            throw new NonRetryableCollectTaskException("采集记录无法序列化", error);
        } catch (NonRetryableCollectTaskException | RetryableCollectTaskException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new RetryableCollectTaskException("Redis 原始队列写入失败", error);
        }
    }

    private Map<String, Object> toRawJob(CollectTask task, Map<String, String> row, int rowNumber) {
        String jobId = firstValue(row, "jobid", "job id", "id");
        if (isBlank(jobId)) {
            jobId = "collect-" + task.getId() + "-" + rowNumber;
        }
        Map<String, Object> company = new LinkedHashMap<>();
        company.put("name", firstValue(row, "companyname", "company", "company name", "company_name"));
        company.put("size", firstValue(row, "companysize", "company size", "company_size", "size"));
        company.put("industry", firstValue(row, "industry", "sector"));
        company.put("type", firstValue(row, "companytype", "company type", "company_type", "type"));

        Map<String, Object> salary = new LinkedHashMap<>();
        salary.put("min", numberOrNull(firstValue(row, "salarymin", "salary min")));
        salary.put("max", numberOrNull(firstValue(row, "salarymax", "salary max")));

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("jobId", jobId);
        job.put("title", firstValue(row, "title", "job title", "job_title", "position"));
        job.put("company", company);
        job.put("salary", salary);
        job.put("city", firstValue(row, "city", "location", "region"));
        job.put("province", firstValue(row, "province"));
        job.put("cityTier", firstValue(row, "citytier", "city tier"));
        job.put("education", firstValue(row, "education", "qualification", "degree"));
        job.put("experience", firstValue(row, "experience", "seniority"));
        job.put("skills", splitValues(firstValue(row, "skills", "key skills", "technologies")));
        job.put("welfare", splitValues(firstValue(row, "welfare", "benefits")));
        job.put("description", firstValue(row, "description", "job description", "job_description"));
        job.put("publishDate", firstValue(row, "publishdate", "publish date", "publish_date", "date"));
        job.put("sourceUrl", firstValue(row, "sourceurl", "source url", "source_url", "url"));
        job.put("crawlTime", LocalDateTime.now().toString());
        return job;
    }

    private Map<String, Integer> headerIndexes(List<String> headers) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            indexes.putIfAbsent(normalizeHeader(headers.get(index)), index);
        }
        return indexes;
    }

    private String firstHeader(Map<String, Integer> indexes, String... names) {
        for (String name : names) {
            if (indexes.containsKey(normalizeHeader(name))) {
                return name;
            }
        }
        return null;
    }

    private Map<String, String> rowFor(List<String> headers, List<String> record) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            row.put(normalizeHeader(headers.get(index)), index < record.size() ? record.get(index).trim() : "");
        }
        return row;
    }

    private String firstValue(Map<String, String> row, String... names) {
        for (String name : names) {
            String value = row.get(normalizeHeader(name));
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Integer numberOrNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> splitValues(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split("[|,，]")) {
            if (!item.isBlank()) {
                values.add(item.trim());
            }
        }
        return values;
    }

    private String sourceMd5(Map<String, Object> job) {
        String value = String.valueOf(job.get("jobId")) + "|"
                + (job.get("sourceUrl") == null ? "" : job.get("sourceUrl"));
        try {
            byte[] bytes = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                hex.append(String.format("%02x", valueByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JVM 不支持 MD5", error);
        }
    }

    private List<String> readRecord(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        StringBuilder record = new StringBuilder(line);
        while (hasOpenQuote(record)) {
            String continuation = reader.readLine();
            if (continuation == null) {
                throw new NonRetryableCollectTaskException("CSV 存在未闭合的引号");
            }
            record.append('\n').append(continuation);
        }
        return parseCsv(record.toString());
    }

    private boolean hasOpenQuote(CharSequence value) {
        boolean quoted = false;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '"') {
                if (quoted && index + 1 < value.length() && value.charAt(index + 1) == '"') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            }
        }
        return quoted;
    }

    private List<String> parseCsv(String record) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < record.length(); index++) {
            char character = record.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < record.length() && record.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        values.add(value.toString());
        if (!values.isEmpty() && !values.get(0).isEmpty() && values.get(0).charAt(0) == '\uFEFF') {
            values.set(0, values.get(0).substring(1));
        }
        return values;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
