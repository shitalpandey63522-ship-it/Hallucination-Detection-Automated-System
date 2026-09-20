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

    @SystemMessage(fromResource = "prompts/topic-extractor-system.txt")
    @UserMessage("Extract the main search topic from this text:\n\n<user_text>{{text}}</user_text>")
    String extractTopic(@V("text") String text);
}
