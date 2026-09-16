package com.hallucination.audit.service;

import com.hallucination.audit.dto.WikipediaSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchServiceTest {

    private WebSearchService webSearchService;

    @BeforeEach
    void setUp() {
        webSearchService = new WebSearchService();
    }

    @Test
    void testBlankQuery() {
        WikipediaSearchResponse response = webSearchService.search("");
        assertNotNull(response);
        assertTrue(response.summary().contains("No query provided"));
    }

    @Test
    void testNullQuery() {
        WikipediaSearchResponse response = webSearchService.search(null);
        assertNotNull(response);
        assertTrue(response.summary().contains("No query provided"));
    }

    @Test
    void testValidQueryFallback() {
        WikipediaSearchResponse response = webSearchService.search("Mount Girnar steps");
        assertNotNull(response);
        assertNotNull(response.summary());
        assertFalse(response.summary().isBlank());
    }
}
