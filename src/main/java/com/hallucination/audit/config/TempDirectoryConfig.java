package com.hallucination.audit.config;

import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class TempDirectoryConfig {

    public static Path initializeTempDirectory(String workspaceRoot) throws IOException {
        Path resolvedWorkspace = Path.of(workspaceRoot).toAbsolutePath().normalize();
        Path tempDir = resolvedWorkspace.resolve(".tmp");

        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        if (!Files.isDirectory(tempDir) || !Files.isWritable(tempDir)) {
            throw new IOException("Temporary directory is not writable: " + tempDir);
        }

        System.setProperty("java.io.tmpdir", tempDir.toString());
        return tempDir;
    }
}
