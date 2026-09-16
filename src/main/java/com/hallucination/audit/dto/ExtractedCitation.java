package com.hallucination.audit.dto;

import dev.langchain4j.model.output.structured.Description;

/**
 * Data structure representing a bibliography or inline citation extracted from audited text
 * and evaluated for hallucination status.
 */
public record ExtractedCitation(
        @Description("The raw inline citation marker or reference item found (e.g. '[1]', '(Smith, 2021)', 'https://doi.org/...').")
        String citationText,

        @Description("The parsed full reference details or publication name (title, author, publication year).")
        String referenceDetail,

        @Description("The validation status: VERIFIED (real publication), UNVERIFIED (inconclusive / private), or HALLUCINATED (fabricated source).")
        String status,

        @Description("Brief explanation justifying the verification verdict.")
        String reasoning,

        @Description("A suggested real, valid academic publication/link if the citation was determined to be hallucinated (otherwise null).")
        String suggestedAlternative
) {}
