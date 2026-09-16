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
            Your task is to rigorously evaluate a single claim against a trusted ground-truth context.

            Follow these step-by-step reasoning rules:
            1. Identify key entities, numbers, dates, locations, and actions in the claim.
            2. Compare these ONLY against facts explicitly stated in the ground-truth context.
            3. Check for numerical mismatches, entity substitutions, or negation flips.
            4. CRITICAL: DO NOT use your internal pre-trained knowledge. If the context does not contain enough information to verify or refute the claim, you MUST classify it as NEUTRAL, regardless of whether you know the claim is true or false in the real world.

            Classify the claim using exactly one of these labels:
            - ENTAILMENT: The claim is strictly supported by facts in the ground-truth context.
            - NEUTRAL: The claim is not mentioned or cannot be verified/refuted from the context.
            - CONTRADICTION: The claim explicitly conflicts with or alters facts in the ground-truth context.

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
