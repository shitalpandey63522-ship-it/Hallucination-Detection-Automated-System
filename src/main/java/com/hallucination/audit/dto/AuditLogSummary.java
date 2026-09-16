package com.hallucination.audit.dto;

import com.hallucination.audit.enums.AuditStatus;

import java.time.Instant;

/**
 * Lightweight view of a persisted audit log for list responses.
 */
public record AuditLogSummary(
        Long id,
        double hallucinationScore,
        AuditStatus status,
        Instant createdAt
) {
}
