package com.hallucination.audit.dto;

import com.hallucination.audit.enums.AuditStatus;

import java.time.Instant;
import java.util.List;

/**
 * Full persisted audit log returned by the detail endpoint.
 */
public record AuditLogResponse(
        Long id,
        String generatedText,
        String groundTruthContext,
        double hallucinationScore,
        AuditStatus status,
        String reasoning,
        List<DetectedContradiction> detectedContradictions,
        List<ExtractedCitation> checkedCitations,
        Instant createdAt
) {
}
