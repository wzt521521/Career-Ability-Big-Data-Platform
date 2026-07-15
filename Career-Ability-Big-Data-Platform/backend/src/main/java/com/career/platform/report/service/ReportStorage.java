package com.career.platform.report.service;

import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Owns report file paths and prevents a database path from escaping the configured report root. */
@Component
public class ReportStorage {

    private final Path root;

    public ReportStorage(@Value("${app.report.output-dir:${REPORT_OUTPUT_DIR:./upload/reports}}") String outputDir) {
        this.root = Paths.get(outputDir).toAbsolutePath().normalize();
    }

    public Path allocatePdf(Long userId) throws IOException {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告用户ID无效");
        }
        Path physicalRoot = physicalRoot();
        Path directory = requireInsideRoot(root.resolve(String.valueOf(userId)));
        Files.createDirectories(directory);
        Path physicalDirectory = requireInsidePhysicalRoot(directory.toRealPath(), physicalRoot);
        return Files.createTempFile(physicalDirectory, "report-", ".pdf");
    }

    public Path resolveExisting(String storedPath) {
        try {
            Path candidate = requireStoredPath(storedPath);
            Path resolved = requireInsidePhysicalRoot(candidate.toRealPath(), physicalRoot());
            if (!Files.isRegularFile(resolved)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "报告文件已被删除");
            }
            return resolved;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告文件不存在");
        }
    }

    public void deleteIfManaged(String storedPath) throws IOException {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        Path candidate = requireStoredPath(storedPath);
        if (!Files.exists(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        requireInsidePhysicalRoot(candidate.toRealPath(), physicalRoot());
        Files.deleteIfExists(candidate);
    }

    public Path getRoot() {
        return root;
    }

    private Path requireInsideRoot(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告文件路径不在受控目录内");
        }
        return normalized;
    }

    private Path requireStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告文件不存在");
        }
        return requireInsideRoot(Paths.get(storedPath));
    }

    private Path physicalRoot() throws IOException {
        Files.createDirectories(root);
        return root.toRealPath();
    }

    private Path requireInsidePhysicalRoot(Path candidate, Path physicalRoot) {
        if (!candidate.startsWith(physicalRoot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告文件路径不在受控目录内");
        }
        return candidate;
    }
}
