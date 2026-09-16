package com.hallucination.audit.dto;

import com.hallucination.audit.enums.NliLabel;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/**
 * Structured NLI evaluation returned by the LangChain4j @AiService layer.
 * Includes optional evidence retrieved from external sources.
 */
public record ClaimEvaluation(
        @Description("The exact claim sentence that was evaluated")
        String claim,

        @Description("NLI label: ENTAILMENT, NEUTRAL, or CONTRADICTION")
        NliLabel label,

        @Description("Brief justification for the assigned NLI label")
        String reasoning,

        @Description("Exact snippet of text that contradicts the claim, if any")
        String contradictingEvidenceSnippet,

        @Description("Optional list of supporting evidence items (title, url, summary)")
        List<com.hallucination.audit.dto.WikipediaSearchResponse> evidence
) {
}
