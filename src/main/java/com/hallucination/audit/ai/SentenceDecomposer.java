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

    @SystemMessage("""
            You are an advanced factual claim decomposition and reasoning engine for hallucination auditing.
            Your task is to analyze large paragraphs or multi-sentence inputs and extract every atomic, verifiable factual claim.
            Rules:
            - Split complex sentences into individual atomic factual statements.
            - Ensure each claim contains an explicit subject, predicate, and specific detail or quantity.
            - CRITICAL: Resolve all pronouns (it, he, she, they, this) to their actual proper noun entities from the text. A claim must never start with 'It' if the entity is known.
            - Preserve dates, names, numerical quantities, locations, and scientific terminology exactly.
            - Skip subjective opinions, greetings, or pure transitional filler.
            - Do not invent claims not explicitly present in the input text.
            """)
    @UserMessage("Perform atomic factual claim decomposition on the following text:\n\n<user_text>{{text}}</user_text>")
    List<String> decompose(@V("text") String text);
}
