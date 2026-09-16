package com.hallucination.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hallucination.audit.dto.AuditLogResponse;
import com.hallucination.audit.dto.AuditLogSummary;
import com.hallucination.audit.dto.DetectedContradiction;
import com.hallucination.audit.dto.ExtractedCitation;
import com.hallucination.audit.exception.AuditLogNotFoundException;
import com.hallucination.audit.model.AuditLogEntity;
import com.hallucination.audit.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private static final TypeReference<List<DetectedContradiction>> CONTRADICTION_LIST_TYPE =
            new TypeReference<>() {};

    private static final TypeReference<List<ExtractedCitation>> CITATION_LIST_TYPE =
            new TypeReference<>() {};

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public List<AuditLogSummary> listRecent(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toSummary)
                .toList();
    }

    public AuditLogResponse getById(@NonNull Long id) {
        AuditLogEntity entity = auditLogRepository.findById(id)
                .orElseThrow(() -> new AuditLogNotFoundException(id));
        return toResponse(entity);
    }

    private AuditLogSummary toSummary(AuditLogEntity entity) {
        return new AuditLogSummary(
                entity.getId(),
                entity.getHallucinationScore(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }

    private AuditLogResponse toResponse(AuditLogEntity entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getGeneratedText(),
                entity.getGroundTruthContext(),
                entity.getHallucinationScore(),
                entity.getStatus(),
                entity.getReasoning(),
                parseContradictions(entity.getDetectedContradictionsJson()),
                parseCitations(entity.getCitationsJson()),
                entity.getCreatedAt()
        );
    }

    private List<DetectedContradiction> parseContradictions(String contradictionsJson) {
        try {
            return objectMapper.readValue(contradictionsJson, CONTRADICTION_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize stored audit contradictions", exception);
        }
    }

    private List<ExtractedCitation> parseCitations(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citationsJson, CITATION_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize stored audit citations", exception);
        }
    }
}
