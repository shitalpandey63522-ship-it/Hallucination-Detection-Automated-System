package com.hallucination.audit.ai;

import com.hallucination.audit.dto.ClaimEvaluation;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;


/**
 * Applies deterministic NLI classification against trusted ground-truth context.
 */
@AiService
public interface HallucinationAuditor {

    @SystemMessage("""
            You are an expert Natural Language Inference (NLI) auditor equipped with chain-of-thought verification.
            Your task is to rigorously evaluate a single claim against a trusted ground-truth reference context.

            Follow these step-by-step reasoning rules:
            1. Identify key entities, numbers, dates, locations, awards, and actions in the claim.
            2. Compare these against facts stated in the ground-truth reference context.
            3. Check for numerical mismatches, entity substitutions, or negation flips.
            4. POST-MORTEM & TEMPORAL RULE: If a claim asserts an event, award, or discovery year occurring AFTER an entity's death year in context (e.g. award in 1925, but died in 1920), classify as CONTRADICTION.
            5. IMPLICIT REFUTATION & AWARD RULE: If a claim attributes a major award (e.g. Nobel Prize) or discovery to a historical figure, but the ground-truth context lists their field/achievements or specifies a different winner/discovery date, classify as CONTRADICTION.
            6. Classify as NEUTRAL ONLY if the topic/entity is completely absent and no temporal, domain, or factual conflict exists.

            Classify the claim using exactly one of these labels:
            - ENTAILMENT: The claim is strictly supported by facts in the ground-truth context.
            - NEUTRAL: The claim is not mentioned and no logical, temporal, or factual conflict exists.
            - CONTRADICTION: The claim explicitly or implicitly conflicts with, alters, or violates dates, awards, or facts in the ground-truth context.

            Be strict and deterministic. Provide concise reasoning explaining your evaluation.
            """)
    @UserMessage("""
            Ground-truth context:
            {{context}}

            Claim to evaluate:
            {{claim}}
            """)
    ClaimEvaluation evaluate(@V("claim") String claim, @V("context") String context);
}
