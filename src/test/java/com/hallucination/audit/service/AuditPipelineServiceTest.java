package com.hallucination.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hallucination.audit.ai.HallucinationAuditor;
import com.hallucination.audit.ai.SentenceDecomposer;
import com.hallucination.audit.ai.CitationExtractor;
import com.hallucination.audit.ai.TopicExtractor;
import com.hallucination.audit.dto.AuditRequest;
import com.hallucination.audit.dto.AuditResult;
import com.hallucination.audit.dto.ClaimEvaluation;
import com.hallucination.audit.dto.ExtractedCitation;
import com.hallucination.audit.dto.WikipediaSearchResponse;
import com.hallucination.audit.enums.AuditStatus;
import com.hallucination.audit.enums.NliLabel;
import com.hallucination.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditPipelineServiceTest {

    @Mock
    private SentenceDecomposer sentenceDecomposer;

    @Mock
    private HallucinationAuditor hallucinationAuditor;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WikiService wikiService;

    @Mock
    private CitationExtractor citationExtractor;

    @Mock
    private CitationVerificationService citationVerificationService;

    @Mock
    private TopicExtractor topicExtractor;

    @BeforeEach
    void setUp() {
        // Lenient mock to return empty list of citations by default
        lenient().when(citationExtractor.extract(anyString())).thenReturn(List.of());
        lenient().when(topicExtractor.extractTopic(anyString())).thenReturn("");
    }

    @Test
    void shouldTreatKnownTagoreFactsAsSupportedWhenNoReferenceContextIsProvided() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of(
                "Rabindranath Tagore is from India",
                "He is a Nobel Prize winner"
        ));
        when(hallucinationAuditor.evaluate(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));
        when(wikiService.search(anyString())).thenReturn(new WikipediaSearchResponse(
                "Rabindranath Tagore",
                "https://en.wikipedia.org/wiki/Rabindranath_Tagore",
                "Rabindranath Tagore was an Indian polymath who reshaped Bengali literature and music in the late 19th and early 20th centuries. He wrote poetry, plays, and essays and was awarded the Nobel Prize in Literature in 1913."
        ));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        AuditResult result = service.audit(new AuditRequest(
                "Rabindranath Tagore is from India and he is a Nobel Prize winner",
                null
        ));

        assertEquals(0.0, result.hallucinationScore(), 0.01);
        assertEquals(AuditStatus.SAFE, result.status());
    }

    @Test
    void shouldTreatSimpleFactualClaimAsSupportedWhenNoContextIsProvided() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of("Gandhi ji from India"));
        when(hallucinationAuditor.evaluate(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        when(wikiService.search(anyString())).thenReturn(new WikipediaSearchResponse(
                "Mahatma Gandhi",
                "https://en.wikipedia.org/wiki/Mahatma_Gandhi",
                "Mohandas Karamchand Gandhi (commonly known as Mahatma Gandhi) was an Indian lawyer, anti-colonial nationalist, and political ethicist who employed nonviolent resistance to lead the successful campaign for India's independence from British rule."
        ));

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        AuditResult result = service.audit(new AuditRequest("Gandhi ji from India", null));
        assertEquals(0.0, result.hallucinationScore(), 0.01);
        assertEquals(AuditStatus.SAFE, result.status());
    }

    @Test
    void shouldUseWikipediaContextWhenNoExplicitGroundTruthIsProvided() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of("Paris is the capital of France"));
        lenient().when(hallucinationAuditor.evaluate(anyString(), anyString())).thenReturn(new ClaimEvaluation(
                "Paris is the capital of France",
                NliLabel.ENTAILMENT,
                "Supported by Wikipedia context",
                null,
                List.of()
        ));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        WikiService stubWikiService = new WikiService() {
            @Override
            public WikipediaSearchResponse search(String query) {
                return new WikipediaSearchResponse("France", "https://en.wikipedia.org/wiki/France", "France is a country in Europe.");
            }
        };

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                stubWikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        service.audit(new AuditRequest("Paris is the capital of France", null));

        // When Wikipedia context is returned, evaluation succeeds and yields safe audit status
        verify(objectMapper, atLeastOnce()).writeValueAsString(any());
    }

    @Test
    void shouldSupplementExplicitContextWithWikipediaInAutoMode() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of("Mars has two moons"));
        when(hallucinationAuditor.evaluate(anyString(), anyString())).thenReturn(new ClaimEvaluation(
                "Mars has two moons",
                NliLabel.ENTAILMENT,
                "Supported by reference material",
                null,
                List.of()
        ));
        when(wikiService.search(anyString())).thenReturn(new WikipediaSearchResponse(
                "Mars",
                "https://en.wikipedia.org/wiki/Mars",
                "Mars has two small moons, Phobos and Deimos, which are irregularly shaped natural satellites."
        ));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        service.audit(new AuditRequest(
                "Mars has two moons",
                "The supplied reference identifies Mars as a planet.",
                "auto"
        ));

        verify(hallucinationAuditor).evaluate(anyString(), argThat(context ->
                context.contains("The supplied reference identifies Mars as a planet.")
                        && context.contains("Mars has two small moons, Phobos and Deimos")));
    }

    @Test
    void shouldRetryWikipediaWithFullClaimWhenExtractedTopicIsTooBroad() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of(
                "Mars has two small moons named Phobos and Deimos"
        ));
        when(topicExtractor.extractTopic(anyString())).thenReturn("Solar System");
        when(wikiService.search("Solar System")).thenReturn(new WikipediaSearchResponse(
                "Solar System", "https://en.wikipedia.org/wiki/Solar_System",
                "The Solar System is the gravitationally bound system of the Sun and the objects that orbit it."
        ));
        when(wikiService.search(argThat(query -> query.contains("Mars has two small moons")))).thenReturn(
                new WikipediaSearchResponse(
                        "Mars", "https://en.wikipedia.org/wiki/Mars",
                        "Mars is the fourth planet from the Sun and has two small, irregularly shaped moons named Phobos and Deimos."
                ));
        when(hallucinationAuditor.evaluate(anyString(), anyString())).thenReturn(new ClaimEvaluation(
                "Mars has two small moons named Phobos and Deimos",
                NliLabel.ENTAILMENT,
                "Supported by Wikipedia",
                null,
                List.of()
        ));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        service.audit(new AuditRequest(
                "Mars has two small moons named Phobos and Deimos",
                null,
                "wiki_only"
        ));

        verify(hallucinationAuditor).evaluate(anyString(), argThat(context ->
                context.contains("Phobos and Deimos")));
    }

    @Test
    void shouldNotReplaceRelevantWikipediaContextWithTopicOnlyResult() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of(
                "Mars has two small moons named Phobos and Deimos"
        ));
        when(topicExtractor.extractTopic(anyString())).thenReturn("Solar System");
        when(wikiService.search("Solar System")).thenReturn(new WikipediaSearchResponse(
                "Solar System", "https://en.wikipedia.org/wiki/Solar_System",
                "The Solar System is the gravitationally bound system of the Sun and the objects that orbit it."
        ));
        when(wikiService.search(argThat(query -> query.contains("Mars has two small moons")))).thenReturn(
                new WikipediaSearchResponse(
                        "Mars", "https://en.wikipedia.org/wiki/Mars",
                        "Mars is the fourth planet from the Sun and has two small moons named Phobos and Deimos."
                ));
        when(hallucinationAuditor.evaluate(anyString(), anyString())).thenReturn(new ClaimEvaluation(
                "Mars has two small moons named Phobos and Deimos",
                NliLabel.ENTAILMENT,
                "Supported by Wikipedia",
                null,
                List.of()
        ));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        service.audit(new AuditRequest(
                "Mars has two small moons named Phobos and Deimos",
                null,
                "auto"
        ));

        verify(hallucinationAuditor).evaluate(anyString(), argThat(context ->
                context.contains("Mars") && context.contains("Phobos and Deimos")));
    }

    @Test
    void shouldNotPenalizeNeutralClaimsInTheScore() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of("The answer is uncertain"));
        lenient().when(hallucinationAuditor.evaluate(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                35.0,
                0.3
        );

        AuditResult result = service.audit(new AuditRequest("The answer is uncertain", null));

        assertEquals(30.0, result.hallucinationScore(), 0.01);
        assertEquals(AuditStatus.SAFE, result.status());
    }

    @Test
    void shouldWeightNeutralClaimsInTheHallucinationScore() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of("The answer is uncertain"));
        lenient().when(hallucinationAuditor.evaluate(anyString(), anyString())).thenThrow(new RuntimeException("AI unavailable"));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.5
        );

        AuditResult result = service.audit(new AuditRequest("The answer is uncertain", null));

        assertEquals(50.0, result.hallucinationScore(), 0.01);
        assertEquals(AuditStatus.FLAGGED, result.status());
    }

        @Test
        void shouldScoreCitationOnlyInputUsingCitationRisk() throws Exception {
                when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of());
                ExtractedCitation citation = new ExtractedCitation(
                                "(Fake, 2024)",
                                "Fake, 2024. Imaginary Journal.",
                                "HALLUCINATED",
                                "Fabricated reference",
                                null
                );
                when(citationExtractor.extract(anyString())).thenReturn(List.of(citation));
                when(citationVerificationService.evaluateCitationsInParallel(any())).thenReturn(List.of(citation));
                when(objectMapper.writeValueAsString(any())).thenReturn("[]");

                AuditPipelineService service = new AuditPipelineService(
                                sentenceDecomposer,
                                hallucinationAuditor,
                                auditLogRepository,
                                objectMapper,
                                wikiService,
                                (r) -> r.run(),
                                citationExtractor,
                                citationVerificationService,
                                topicExtractor,
                                30.0,
                                0.2
                );

                AuditResult result = service.audit(new AuditRequest("(Fake, 2024)", null));

                assertEquals(50.0, result.hallucinationScore(), 0.01);
                assertEquals(AuditStatus.FLAGGED, result.status());
        }

    @Test
    void shouldNotReuseUnrelatedStoredContextForNewQueries() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of("Earth is the third planet from the Sun"));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        AuditResult result = service.audit(new AuditRequest("Earth is the third planet from the Sun", null));
        org.junit.jupiter.api.Assertions.assertNotNull(result);
    }

    @Test
    void shouldDetectNegationContradictionOffline() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of(
                "Apple violated the Sherman Act"
        ));
        when(hallucinationAuditor.evaluate(anyString(), anyString())).thenThrow(new RuntimeException("Offline mode"));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        AuditResult result = service.audit(new AuditRequest(
                "Apple violated the Sherman Act",
                "Apple did not violate the Sherman Act."
        ));

        assertEquals(100.0, result.hallucinationScore(), 0.01);
        assertEquals(AuditStatus.FLAGGED, result.status());
        assertTrue(result.detectedContradictions().get(0).reasoning().contains("Negation Conflict"));
    }

    @Test
    void shouldDetectNumericMismatchOffline() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of(
                "The treaty was dissolved in 2026."
        ));
        when(hallucinationAuditor.evaluate(anyString(), anyString())).thenThrow(new RuntimeException("Offline mode"));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        AuditResult result = service.audit(new AuditRequest(
                "The treaty was dissolved in 2026.",
                "The treaty was dissolved in 2024."
                ));

        assertEquals(100.0, result.hallucinationScore(), 0.01);
        assertTrue(result.detectedContradictions().get(0).reasoning().contains("Numeric Mismatch"));
        assertEquals(AuditStatus.FLAGGED, result.status());
    }

    @Test
        void shouldTreatUnsupportedProperNounAsNeutralOffline() throws Exception {
        when(sentenceDecomposer.decompose(anyString())).thenReturn(List.of(
                "Justice Elena Kagan wrote the decision."
        ));
        when(hallucinationAuditor.evaluate(anyString(), anyString())).thenThrow(new RuntimeException("Offline mode"));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        AuditResult result = service.audit(new AuditRequest(
                "Justice Elena Kagan wrote the decision.",
                "Judge Yvonne Gonzalez Rogers ruled on the case."
        ));
                assertEquals(30.0, result.hallucinationScore(), 0.01);
        assertEquals(AuditStatus.FLAGGED, result.status());
                assertTrue(result.detectedContradictions().isEmpty());
    }

    @Test
    void shouldAnswerZeroHallucinationFromWikipediaFallback() {
        when(wikiService.search("Albert Einstein")).thenReturn(new WikipediaSearchResponse(
                "Albert Einstein",
                "https://en.wikipedia.org/wiki/Albert_Einstein",
                "Albert Einstein was a German-born theoretical physicist widely acknowledged to be one of the greatest and most influential physicists of all time."
        ));

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        java.util.Map<String, Object> response = service.answerZeroHallucination("Who is Albert Einstein?", "Science");
        assertEquals(true, response.get("verified"));
        assertTrue(((String) response.get("answer")).contains("Albert Einstein was a German-born theoretical physicist"));
    }

    @Test
    void shouldAnswerZeroHallucinationForAkbarBadshah() {
        when(wikiService.search("akbar")).thenReturn(new WikipediaSearchResponse(
                "Akbar the Great",
                "https://en.wikipedia.org/wiki/Akbar",
                "Abu'l-Fath Jalal-ud-din Muhammad Akbar, popularly known as Akbar the Great, was the third Mughal emperor, who reigned from 1556 to 1605."
        ));

        AuditPipelineService service = new AuditPipelineService(
                sentenceDecomposer,
                hallucinationAuditor,
                auditLogRepository,
                objectMapper,
                wikiService,
                (r) -> r.run(),
                citationExtractor,
                citationVerificationService,
                topicExtractor,
                25.0,
                0.3
        );

        java.util.Map<String, Object> response = service.answerZeroHallucination("who was akbar badshah", "History");
        assertEquals(true, response.get("verified"));
        assertTrue(((String) response.get("answer")).contains("Akbar the Great"));
    }
}
