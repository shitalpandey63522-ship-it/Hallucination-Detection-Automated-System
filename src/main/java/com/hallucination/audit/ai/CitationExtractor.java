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

    @SystemMessage("""
            You are a citation extraction engine. Your task is to extract all citations, reference markers, URLs, DOIs, or bibliography/reference entries from the text.
            For each citation found:
            - Provide the literal text used as the citation in the source (e.g. "[1]", "(Smith, 2021)", or an inline URL).
            - Reconstruct or copy the full publication details in 'referenceDetail' (e.g., "Smith, J. (2021). Hallucinations in LLMs. AI Journal."). If it's a URL, write the URL name/site.
            - Set status to 'UNVERIFIED', reasoning to 'Pending check', and suggestedAlternative to null.
            Do not make up citations. Only extract what is explicitly or implicitly cited in the text.
            """)
    @UserMessage("Extract all citations from this text:\n\n<user_text>{{text}}</user_text>")
    List<ExtractedCitation> extract(@V("text") String text);
}
