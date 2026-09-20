package com.hallucination.audit.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/**
 * Extracts the primary search topic (entity, event, or concept) from a text
 * to fetch context from Wikipedia.
 */
@AiService
public interface TopicExtractor {

    @SystemMessage("""
            You are a search query extraction engine.
            Your task is to analyze the provided text and extract the single most relevant entity, event, concept, or case name to use as a search query (e.g. for looking up on Wikipedia).
            Examples:
            - Text: "Albert Einstein published the theory of relativity in 1916..." -> Output: "General relativity"
            - Text: "Certainly! The case is Apple Inc. v. Epic Games Inc., 594 U.S. 412..." -> Output: "Apple Inc. v. Epic Games"
            - Text: "The capital of France is Paris." -> Output: "Paris"
            
            Return ONLY the extracted search phrase. Do not include introductory phrases, punctuation, or explanations.
            """)
    @UserMessage("Extract the main search topic from this text:\n\n<user_text>{{text}}</user_text>")
    String extractTopic(@V("text") String text);
}
