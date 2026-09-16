package com.hallucination.audit.dto;

import com.hallucination.audit.ai.ProjectContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestDefaultsTest {

    @Test
    void shouldAllowNullContextWhenAuditRequestContextIsMissing() {
        AuditRequest request = new AuditRequest("The sky is blue.", null);

        org.junit.jupiter.api.Assertions.assertNull(request.groundTruthContext());
    }

    @Test
    void shouldUseDefaultContextWhenPipelineRequestContextIsMissing() {
        AuditPipelineRequest request = new AuditPipelineRequest("What is the sky color?", null);

        assertNotNull(request.context());
        assertEquals(ProjectContext.DEFAULT_GROUND_TRUTH_CONTEXT, request.context());
    }
}
