package com.hallucination.audit.controller;

import com.hallucination.audit.dto.AuditLogResponse;
import com.hallucination.audit.dto.AuditLogSummary;
import com.hallucination.audit.dto.AuditPipelineRequest;
import com.hallucination.audit.dto.AuditPipelineResponse;
import com.hallucination.audit.dto.AuditRequest;
import com.hallucination.audit.dto.AuditResult;
import com.hallucination.audit.service.AuditLogService;
import com.hallucination.audit.service.AuditPipelineOrchestrator;
import com.hallucination.audit.service.AuditPipelineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditPipelineService auditPipelineService;
    private final AuditLogService auditLogService;
    private final AuditPipelineOrchestrator auditPipelineOrchestrator;

    public AuditController(
            AuditPipelineService auditPipelineService,
            AuditLogService auditLogService,
            AuditPipelineOrchestrator auditPipelineOrchestrator
    ) {
        this.auditPipelineService = auditPipelineService;
        this.auditLogService = auditLogService;
        this.auditPipelineOrchestrator = auditPipelineOrchestrator;
    }

    @PostMapping
    public ResponseEntity<AuditResult> audit(@Valid @RequestBody AuditRequest request) {
        AuditResult result = auditPipelineService.audit(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/pipeline")
    public ResponseEntity<AuditPipelineResponse> auditPipeline(@Valid @RequestBody AuditPipelineRequest request) {
        AuditPipelineResponse result = auditPipelineOrchestrator.processWithAudit(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/qa")
    public ResponseEntity<java.util.Map<String, Object>> answerZeroHallucination(
            @RequestParam(required = false) String topic,
            @RequestBody String question
    ) {
        return ResponseEntity.ok(auditPipelineService.answerZeroHallucination(question, topic));
    }

    @GetMapping
    public ResponseEntity<List<AuditLogSummary>> listRecent(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(auditLogService.listRecent(limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getById(@PathVariable long id) {
        return ResponseEntity.ok(auditLogService.getById(id));
    }
}
