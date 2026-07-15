package com.career.platform.report.service;

import com.career.platform.common.error.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportStorageTest {

    @Test
    void allocatesAndResolvesOnlyFilesBelowItsConfiguredRoot(@TempDir Path temporaryDirectory) throws Exception {
        ReportStorage storage = new ReportStorage(temporaryDirectory.toString());
        Path file = storage.allocatePdf(17L);
        Files.write(file, new byte[]{1, 2, 3});

        assertTrue(file.startsWith(temporaryDirectory));
        assertEquals(file, storage.resolveExisting(file.toString()));
        storage.deleteIfManaged(file.toString());
        assertTrue(Files.notExists(file));
    }

    @Test
    void rejectsAStoredPathOutsideTheReportRoot(@TempDir Path temporaryDirectory) throws Exception {
        ReportStorage storage = new ReportStorage(temporaryDirectory.resolve("reports").toString());
        Path outside = Files.write(temporaryDirectory.resolve("outside.pdf"), new byte[]{1});

        assertThrows(BusinessException.class, () -> storage.resolveExisting(outside.toString()));
        assertThrows(BusinessException.class, () -> storage.deleteIfManaged(outside.toString()));
    }

    @Test
    void rejectsADirectorySymlinkThatEscapesTheReportRoot(@TempDir Path temporaryDirectory) throws Exception {
        Path root = temporaryDirectory.resolve("reports");
        Path outsideDirectory = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Path outsideFile = Files.write(outsideDirectory.resolve("report.pdf"), new byte[]{1});
        Files.createDirectories(root);
        Path linkedUserDirectory = root.resolve("17");

        try {
            Files.createSymbolicLink(linkedUserDirectory, outsideDirectory);
        } catch (UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
            return;
        } catch (java.io.IOException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
            return;
        }

        ReportStorage storage = new ReportStorage(root.toString());
        Path escapedPath = linkedUserDirectory.resolve(outsideFile.getFileName());

        assertThrows(BusinessException.class, () -> storage.resolveExisting(escapedPath.toString()));
        assertThrows(BusinessException.class, () -> storage.deleteIfManaged(escapedPath.toString()));
        assertTrue(Files.exists(outsideFile));
    }
}
