package com.hallucination.audit.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

import java.util.List;

/**
 * Decomposes generated LLM output into granular, sentence-level factual claims.
 */
@AiService
public interface SentenceDecomposer {

    @SystemMessage(fromResource = "prompts/sentence-decomposer-system.txt")
    @UserMessage("Perform atomic factual claim decomposition on the following text:\n\n<user_text>{{text}}</user_text>")
    List<String> decompose(@V("text") String text);
}
