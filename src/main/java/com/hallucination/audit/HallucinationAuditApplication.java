package com.hallucination.audit;

import com.hallucination.audit.config.TempDirectoryConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HallucinationAuditApplication {

    public static void main(String[] args) {
        try {
            TempDirectoryConfig.initializeTempDirectory(System.getProperty("user.dir"));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize temporary directory for the application", exception);
        }

        SpringApplication.run(HallucinationAuditApplication.class, args);
    }
}