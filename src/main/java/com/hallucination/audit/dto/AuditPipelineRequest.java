package com.hallucination.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hallucination.audit.ai.ProjectContext;

public record AuditPipelineRequest(
    @JsonProperty("question") String question,
    @JsonProperty("context") String context
) {
    public AuditPipelineRequest {
        if (context == null || context.isBlank()) {
            context = ProjectContext.DEFAULT_GROUND_TRUTH_CONTEXT;
        }
    }
}
