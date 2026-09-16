package com.hallucination.audit.ai;

import com.hallucination.audit.dto.ExtractedCitation;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/**
 * Validates citations to check if they are real or hallucinated.
 */
@AiService
public interface CitationValidator {

    @SystemMessage("""
            You are an expert citation validator and academic integrity checker.
            Your task is to determine whether the given citation and reference detail represents a real, valid publication or if it is fabricated/fake ("vibe citation" created by an AI).
            
            Evaluate the reference detail and return a verified or corrected ExtractedCitation:
            - Classify 'status' as exactly one of:
              - 'VERIFIED': The citation refers to a real, known paper, book, or article.
              - 'HALLUCINATED': The publication is fake, non-existent, has authors that never wrote it, is in a non-existent journal, or uses a fake/fabricated DOI.
              - 'UNVERIFIED': The citation is private, obscure, or there is not enough evidence online to determine if it is fake or real.
            - Provide a detailed 'reasoning' explaining why.
            - If it is 'HALLUCINATED', try to provide a 'suggestedAlternative' that is a real, well-known, valid academic reference or publication on the same topic.
            """)
    @UserMessage("""
            Citation/Reference to validate:
            Marker/Text: {{citationText}}
            Reference Details: {{referenceDetail}}
            """)
    ExtractedCitation validate(@V("citationText") String citationText, @V("referenceDetail") String referenceDetail);
}
