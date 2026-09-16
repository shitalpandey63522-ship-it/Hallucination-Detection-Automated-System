package com.hallucination.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuditPipelineResponse(
    @JsonProperty("generatedResponse") String generatedResponse,
    @JsonProperty("auditScore") Double auditScore,
    @JsonProperty("auditStatus") String auditStatus,
    @JsonProperty("isSafe") Boolean isSafe,
    @JsonProperty("auditDetails") AuditResult auditDetails
) {}
