package com.hallucination.audit.dto;

public record WikipediaSearchResponse(
        String title,
        String url,
        String summary
) {
}
