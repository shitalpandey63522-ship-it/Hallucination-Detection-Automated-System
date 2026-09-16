package com.hallucination.audit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalVectorRagServiceTest {

    private LocalVectorRagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new LocalVectorRagService();
        ragService.initSeedKnowledgeBase();
    }

    @Test
    void shouldFindRelevantContextFromSeedKnowledgeBase() {
        String query = "Where was Gandhi born?";
        String match = ragService.findRelevantContext(query);

        assertNotNull(match);
        assertTrue(match.contains("Porbandar") || match.contains("Gandhi"));
    }

    @Test
    void shouldIngestAndRetrieveCustomDocument() {
        String docId = "quantum-physics";
        String content = "Quantum entanglement is a phenomenon in quantum physics where two particles remain connected so that actions performed on one affect the other instantly.";

        ragService.ingestDocument(docId, content);

        String match = ragService.findRelevantContext("What is quantum entanglement?");
        assertNotNull(match);
        assertTrue(match.contains("entanglement") || match.contains("particles"));
    }

    @Test
    void shouldReturnNullForUnmatchedQueries() {
        String match = ragService.findRelevantContext("xyzzy unmapped nonsensical query 12345");
        assertNull(match);
    }

    @Test
    void shouldNotMixUnrelatedDocumentsIntoMultiWordQueries() {
        String match = ragService.findRelevantContext("Where was Mahatma Gandhi born?");

        assertNotNull(match);
        assertTrue(match.contains("Porbandar"));
        assertFalse(match.contains("Quantum entanglement"));
    }
}
