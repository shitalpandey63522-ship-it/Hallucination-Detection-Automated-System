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

    @SystemMessage(fromResource = "prompts/hallucination-auditor-system.txt")
    @UserMessage("""
            Ground-truth context:
            <reference_context>{{context}}</reference_context>

            Claim to evaluate:
            <user_claim>{{claim}}</user_claim>
            """)
    ClaimEvaluation evaluate(@V("claim") String claim, @V("context") String context);
}
