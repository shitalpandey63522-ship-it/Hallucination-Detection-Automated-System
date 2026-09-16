package com.hallucination.audit.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TempDirectoryConfigTest {

    @Test
    void shouldCreateWritableTempDirectoryInsideWorkspace() throws IOException {
        Path workspaceDir = Files.createTempDirectory("hallucination-audit-test");

        try {
            Path tempDir = TempDirectoryConfig.initializeTempDirectory(workspaceDir.toString());

            assertTrue(Files.isDirectory(tempDir));
            assertTrue(Files.isWritable(tempDir));
            assertTrue(System.getProperty("java.io.tmpdir") != null);
        } finally {
            Files.deleteIfExists(workspaceDir.resolve(".tmp"));
            Files.deleteIfExists(workspaceDir);
            System.clearProperty("java.io.tmpdir");
        }
    }
}
