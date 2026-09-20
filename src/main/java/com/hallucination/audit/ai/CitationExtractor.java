package com.hallucination.audit.ai;

import com.hallucination.audit.dto.ExtractedCitation;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

import java.util.List;

/**
 * Extracts citations and references from text.
 */
@AiService
public interface CitationExtractor {

    @SystemMessage(fromResource = "prompts/citation-extractor-system.txt")
    @UserMessage("Extract all citations from this text:\n\n<user_text>{{text}}</user_text>")
    List<ExtractedCitation> extract(@V("text") String text);
}
