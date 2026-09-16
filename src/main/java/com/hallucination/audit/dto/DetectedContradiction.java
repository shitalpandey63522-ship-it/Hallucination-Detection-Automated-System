package com.hallucination.audit.dto;

import com.hallucination.audit.enums.NliLabel;
import java.util.List;

/**
 * A single detected factual anomaly mapped to its NLI classification.
 * Carries optional evidence items used during evaluation.
 */
public record DetectedContradiction(
        String claim,
        NliLabel label,
        String reasoning,
        List<WikipediaSearchResponse> evidence
) {
}
