package com.hallucination.audit.dto;

import com.hallucination.audit.enums.AuditStatus;

import java.util.List;

/**
 * Immutable audit verdict returned to the calling application.
 * Includes the original per-claim evaluations so callers can display evidence and per-claim reasoning.
 */
public record AuditResult(
        double hallucinationScore,
        AuditStatus status,
        String reasoning,
        List<DetectedContradiction> detectedContradictions,
        List<ClaimEvaluation> evaluations,
        List<ExtractedCitation> checkedCitations
) {
}
