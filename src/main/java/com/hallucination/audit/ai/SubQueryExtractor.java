package com.hallucination.audit.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import java.util.List;

/**
 * Decomposes complex claims into targeted search sub-queries for recursive parallel retrieval.
 */
@AiService
public interface SubQueryExtractor {

    @SystemMessage("""
            You are a search query decomposition engine for multi-hop fact verification.
            Analyze the given claim and extract 1 to 3 concise, targeted search sub-queries to verify specific assertions, awards, dates, discoveries, or events mentioned in the claim.

            Examples:
            - Claim: "Srinivasa Ramanujan won the Nobel Prize in Physics in 1925."
              Sub-queries: ["Srinivasa Ramanujan awards", "Nobel Prize in Physics 1925 winner"]

            - Claim: "Srinivasa Ramanujan discovered string theory."
              Sub-queries: ["Srinivasa Ramanujan discoveries", "String theory discovery date history"]

            Return ONLY the extracted search phrases as a list. Keep queries short and factual.
            """)
    @UserMessage("Extract factual sub-queries from this claim:\n\n<user_claim>{{claim}}</user_claim>")
    List<String> extractSubQueries(@V("claim") String claim);
}
