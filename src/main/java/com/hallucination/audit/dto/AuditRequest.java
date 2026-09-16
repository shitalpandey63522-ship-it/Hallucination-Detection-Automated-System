package com.hallucination.audit.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Incoming payload containing generated LLM text and trusted reference context.
 */
public record AuditRequest(
        @NotBlank(message = "generatedText must not be blank")
        String generatedText,

        String groundTruthContext,
        
        String contextSourceMode
) {
    public AuditRequest(String generatedText, String groundTruthContext) {
        this(generatedText, groundTruthContext, "auto");
    }

    public AuditRequest {
        if (groundTruthContext != null && groundTruthContext.isBlank()) {
            groundTruthContext = null;
        }
        if (contextSourceMode == null || contextSourceMode.isBlank()) {
            contextSourceMode = "auto";
        }
    }
}
