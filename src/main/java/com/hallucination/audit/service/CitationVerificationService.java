package com.hallucination.audit.service;

import com.hallucination.audit.ai.CitationValidator;
import com.hallucination.audit.dto.ExtractedCitation;
import com.hallucination.audit.dto.WikipediaSearchResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CitationVerificationService {

    private final CitationValidator citationValidator;
    private final WikiService wikiService;
    private final ExecutorService executorService;

    public CitationVerificationService(CitationValidator citationValidator, WikiService wikiService, ExecutorService executorService) {
        this.citationValidator = citationValidator;
        this.wikiService = wikiService;
        this.executorService = executorService;
    }

    public List<ExtractedCitation> evaluateCitationsInParallel(List<ExtractedCitation> rawCitations) {
        if (rawCitations == null || rawCitations.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<ExtractedCitation>> futures = rawCitations.stream()
                .map(citation -> CompletableFuture.supplyAsync(
                        () -> evaluateCitation(citation),
                        executorService
                ).exceptionally(ex -> fallbackCitationEvaluation(citation)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(f -> f.join())
                .collect(Collectors.toList());
    }

    private ExtractedCitation fallbackCitationEvaluation(ExtractedCitation citation) {
        if (citation.status() != null && !citation.status().isBlank()) {
            return citation;
        }
        return new ExtractedCitation(
                citation.citationText(),
                citation.referenceDetail(),
                "UNVERIFIED",
                "Validation service offline or timed out.",
                null
        );
    }

    private ExtractedCitation evaluateCitation(ExtractedCitation citation) {
        try {
            ExtractedCitation evaluated = citationValidator.validate(citation.citationText(), citation.referenceDetail());
            if (evaluated == null) {
                return fallbackCitationEvaluation(citation);
            }

            AuthorYear parsed = parseAuthorAndYear(evaluated.referenceDetail());
            String author = parsed.author();
            Integer yearStr = parsed.year();

            if (yearStr != null && yearStr > LocalDate.now().getYear() + 1) {
                return new ExtractedCitation(
                        evaluated.citationText(),
                        evaluated.referenceDetail(),
                        "HALLUCINATED",
                        "The citation year (" + yearStr + ") is in the future. This implies fabrication.",
                        evaluated.suggestedAlternative()
                );
            }

            if (author != null && yearStr != null) {
                WikipediaSearchResponse authorResp = wikiService.search(author);
                if (authorResp != null && authorResp.summary() != null) {
                    Integer deathYear = extractDeathYearFromWikipedia(authorResp.summary());
                    if (deathYear != null && yearStr > deathYear) {
                        return new ExtractedCitation(
                                evaluated.citationText(),
                                evaluated.referenceDetail(),
                                "HALLUCINATED",
                                author + " passed away in " + deathYear + ". They could not have published a paper/work in " + yearStr + ".",
                                authorResp.title() + " (" + deathYear + ") is historical reference."
                        );
                    }
                }
            }
            return evaluated;
        } catch (Exception e) {
            return fallbackCitationEvaluation(citation);
        }
    }

    private record AuthorYear(String author, Integer year) {}

    private AuthorYear parseAuthorAndYear(String referenceDetail) {
        if (referenceDetail == null) return new AuthorYear(null, null);

        Pattern yearPattern = Pattern.compile("\\b(19|20)\\d{2}\\b");
        Matcher yearMatcher = yearPattern.matcher(referenceDetail);
        Integer year = null;
        if (yearMatcher.find()) {
            try {
                year = Integer.parseInt(yearMatcher.group(0));
            } catch (Exception ignored) {
            }
        }

        String author = null;
        String[] parts = referenceDetail.split(",");
        if (parts.length > 0) {
            author = parts[0].replaceAll("[^a-zA-Z\\s]", "").trim();
            if (author.endsWith(" et al")) {
                author = author.substring(0, author.length() - 6).trim();
            }
        }
        return new AuthorYear(author, year);
    }

    private Integer extractDeathYearFromWikipedia(String summary) {
        if (summary == null) return null;

        Pattern diedPattern = Pattern.compile("died(?:\\s+(?:on|in))?\\s+.*?(\\b(?:18|19|20)\\d{2}\\b)", Pattern.CASE_INSENSITIVE);
        Matcher m1 = diedPattern.matcher(summary);
        if (m1.find()) {
            try {
                return Integer.parseInt(m1.group(1));
            } catch (Exception ignored) {}
        }

        Pattern parenthesisPattern = Pattern.compile("\\(.*?(\\b(?:18|19|20)\\d{2}\\b)\\s*[-–—]\\s*(\\b(?:18|19|20)\\d{2}\\b).*?\\)");
        Matcher m2 = parenthesisPattern.matcher(summary);
        if (m2.find()) {
            try {
                return Integer.parseInt(m2.group(2));
            } catch (Exception ignored) {}
        }
        return null;
    }
}
