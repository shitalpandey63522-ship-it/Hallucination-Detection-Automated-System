package com.hallucination.audit.service;

import com.hallucination.audit.ai.AgentResponseGenerator;
import com.hallucination.audit.dto.AuditPipelineRequest;
import com.hallucination.audit.dto.AuditPipelineResponse;
import com.hallucination.audit.dto.AuditRequest;
import com.hallucination.audit.dto.AuditResult;
import com.hallucination.audit.enums.AuditStatus;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the complete end-to-end pipeline:
 * 1. Agent generates response to query
 * 2. Hallucination audit checks the response
 * 3. Returns safe response with audit results
 */
@Service
public class AuditPipelineOrchestrator {

    private final AgentResponseGenerator agentResponseGenerator;
    private final AuditPipelineService auditPipelineService;

    public AuditPipelineOrchestrator(
            AgentResponseGenerator agentResponseGenerator,
            AuditPipelineService auditPipelineService
    ) {
        this.agentResponseGenerator = agentResponseGenerator;
        this.auditPipelineService = auditPipelineService;
    }

    /**
     * Complete pipeline: Generate response → Audit for hallucinations → Return result
     *
     * @param request - Contains question and ground truth context
     * @return Response with generated text, audit score, and audit details
     */
    public AuditPipelineResponse processWithAudit(AuditPipelineRequest request) {
        // Step 1: Agent generates response to the question using the context
        String generatedResponse = agentResponseGenerator.generateResponse(
                request.question(),
                request.context()
        );

        // Step 2: Audit the generated response against the resolved context
        AuditRequest auditRequest = new AuditRequest(generatedResponse, request.context());
        AuditResult auditResult = auditPipelineService.audit(auditRequest);

        // Step 3: Determine if the response is safe
        boolean isSafe = auditResult.status() == AuditStatus.SAFE;

        // Step 4: Return the complete pipeline result
        return new AuditPipelineResponse(
                generatedResponse,
                auditResult.hallucinationScore(),
                auditResult.status().toString(),
                isSafe,
                auditResult
        );
    }
}
