package com.hallucination.audit.model;

import com.hallucination.audit.enums.AuditStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(nullable = false)
    private String generatedText;

    @Lob
    @Column(nullable = false)
    private String groundTruthContext;

    @Column(nullable = false)
    private double hallucinationScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditStatus status;

    @Lob
    @Column(nullable = false)
    private String reasoning;

    @Lob
    @Column(nullable = false)
    private String detectedContradictionsJson;

    @Lob
    @Column(nullable = false)
    private String citationsJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLogEntity() {
    }

    public AuditLogEntity(
            String generatedText,
            String groundTruthContext,
            double hallucinationScore,
            AuditStatus status,
            String reasoning,
            String detectedContradictionsJson,
            String citationsJson
    ) {
        this.generatedText = generatedText;
        this.groundTruthContext = groundTruthContext;
        this.hallucinationScore = hallucinationScore;
        this.status = status;
        this.reasoning = reasoning;
        this.detectedContradictionsJson = detectedContradictionsJson;
        this.citationsJson = citationsJson;
    }

    public Long getId() {
        return id;
    }

    public String getGeneratedText() {
        return generatedText;
    }

    public String getGroundTruthContext() {
        return groundTruthContext;
    }

    public double getHallucinationScore() {
        return hallucinationScore;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public String getReasoning() {
        return reasoning;
    }

    public String getDetectedContradictionsJson() {
        return detectedContradictionsJson;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
