package com.hallucination.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hallucination.audit.ai.HallucinationAuditor;
import com.hallucination.audit.ai.ProjectContext;
import com.hallucination.audit.ai.SentenceDecomposer;
import com.hallucination.audit.ai.CitationExtractor;
import com.hallucination.audit.ai.TopicExtractor;
import com.hallucination.audit.dto.AuditRequest;
import com.hallucination.audit.dto.AuditResult;
import com.hallucination.audit.dto.ClaimEvaluation;
import com.hallucination.audit.dto.DetectedContradiction;
import com.hallucination.audit.dto.ExtractedCitation;
import com.hallucination.audit.enums.AuditStatus;
import com.hallucination.audit.enums.NliLabel;
import com.hallucination.audit.model.AuditLogEntity;
import com.hallucination.audit.repository.AuditLogRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class AuditPipelineService {

    private final SentenceDecomposer sentenceDecomposer;
    private final HallucinationAuditor hallucinationAuditor;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final WikiService wikiService;
    private final Executor auditExecutor;
    private final CitationExtractor citationExtractor;
    private final CitationVerificationService citationVerificationService;
    private final TopicExtractor topicExtractor;
    private final double flagThreshold;
    private final double neutralWeight;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LocalVectorRagService localVectorRagService;

    @Value("${gemini.api-key:}")
    private String geminiApiKey = "test-key";

    public AuditPipelineService(
            SentenceDecomposer sentenceDecomposer,
            HallucinationAuditor hallucinationAuditor,
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper,
            WikiService wikiService,
            @Qualifier("auditExecutor") Executor auditExecutor,
            CitationExtractor citationExtractor,
            CitationVerificationService citationVerificationService,
            TopicExtractor topicExtractor,
            @Value("${audit.flag-threshold:100.0}") double flagThreshold,
            @Value("${audit.neutral-weight:0.8}") double neutralWeight
    ) {
        this.sentenceDecomposer = sentenceDecomposer;
        this.hallucinationAuditor = hallucinationAuditor;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.wikiService = wikiService;
        this.auditExecutor = auditExecutor;
        this.citationExtractor = citationExtractor;
        this.citationVerificationService = citationVerificationService;
        this.topicExtractor = topicExtractor;
        this.flagThreshold = flagThreshold;
        this.neutralWeight = neutralWeight;
    }

    public AuditResult audit(AuditRequest request) {
        String effectiveContext = resolveContext(request);
        
        List<String> rawClaims;
        try {
            rawClaims = sentenceDecomposer.decompose(request.generatedText());
        } catch (Exception e) {
            rawClaims = LocalNlpUtils.splitSentencesLocally(request.generatedText());
        }
        if (rawClaims == null) {
            rawClaims = List.of();
        }
        List<String> claims = java.util.Objects.requireNonNull(normalizeClaims(rawClaims), "Claims list must not be null");

        List<ExtractedCitation> rawCitations;
        try {
            rawCitations = citationExtractor.extract(request.generatedText());
        } catch (Exception e) {
            rawCitations = LocalNlpUtils.extractCitationsLocally(request.generatedText());
        }
        if (rawCitations == null) {
            rawCitations = List.of();
        }
        List<ExtractedCitation> checkedCitations = citationVerificationService.evaluateCitationsInParallel(rawCitations);

        if (claims.isEmpty()) {
            long hallucinatedCitationCount = checkedCitations.stream()
                .filter(c -> "HALLUCINATED".equalsIgnoreCase(c.status()))
                .count();
            boolean hasHallucinatedCitation = hallucinatedCitationCount > 0;
            double citationRiskScore = checkedCitations.isEmpty()
                ? 0.0
                : ((double) hallucinatedCitationCount / checkedCitations.size()) * 50.0;
            AuditStatus status = hasHallucinatedCitation ? AuditStatus.FLAGGED : AuditStatus.SAFE;
            String message = hasHallucinatedCitation
                    ? "No evaluable factual claims were found, but fabricated citations were detected."
                    : "No evaluable factual claims or hallucinated citations were found in the text.";
            AuditResult result = new AuditResult(
                    roundScore(citationRiskScore),
                    status,
                    message,
                    List.of(),
                    List.of(),
                    checkedCitations
            );
            persistAuditLog(request, result);
            return result;
        }

        List<ClaimEvaluation> evaluations;
        if (effectiveContext == null || effectiveContext.isBlank()) {
            evaluations = evaluateClaimsInParallel(claims, "");
        } else {
            evaluations = evaluateClaimsInParallel(claims, effectiveContext);
        }
        AuditResult result = buildAuditResult(evaluations, checkedCitations);
        persistAuditLog(new AuditRequest(request.generatedText(), effectiveContext != null ? effectiveContext : ""), result);
        return result;
    }

    public Map<String, Object> answerZeroHallucination(String question, String topic) {
        if (question == null || question.isBlank()) {
            return Map.of("status", "error", "message", "Question must not be blank.");
        }

        String ragContext = null;
        String wikiContext = null;
        String wikiTitle = null;
        String wikiUrl = null;

        // Step 1: Query Local Vector RAG Store (Search topic first, then fall back to all documents across topics)
        if (localVectorRagService != null) {
            String candidate = localVectorRagService.findRelevantContext(topic, question);
            if (candidate == null || candidate.isBlank()) {
                candidate = localVectorRagService.findRelevantContext("all", question);
            }
            if (candidate != null && !candidate.isBlank()) {
                ragContext = candidate;
            }
        }

        // Step 2: Query Live Wikipedia API (Always query Wikipedia so facts are always available)
        try {
            String extractedTopic = extractTopic(question);
            com.hallucination.audit.dto.WikipediaSearchResponse wikiResp = null;
            if (extractedTopic != null && !extractedTopic.isBlank()) {
                wikiResp = wikiService.search(extractedTopic);
            }
            if (wikiResp == null || wikiResp.summary() == null || wikiResp.summary().length() <= 50 || wikiResp.summary().toLowerCase().contains("unavailable")) {
                wikiResp = wikiService.search(question);
            }

            if (wikiResp != null && wikiResp.summary() != null && wikiResp.summary().length() > 50 && !wikiResp.summary().toLowerCase().contains("unavailable")) {
                wikiContext = wikiResp.summary();
                wikiTitle = wikiResp.title() != null ? wikiResp.title() : extractedTopic;
                wikiUrl = wikiResp.url();
            }
        } catch (Exception ignored) {}

        // Step 3: Zero-Hallucination Guardrail Check (If neither RAG nor Wikipedia has facts)
        if ((ragContext == null || ragContext.isBlank()) && (wikiContext == null || wikiContext.isBlank())) {
            return Map.of(
                "question", question,
                "answer", "⚠️ Zero-Hallucination Shield: No verified reference facts found in Local Vector RAG or Wikipedia for this query. Refusing to guess to guarantee 0% hallucination.",
                "verified", false,
                "source", "None",
                "contextUsed", "None (Zero-Hallucination Shield Engaged)"
            );
        }

        // Step 4: Synthesize Combined Grounded Response with Explicit Source Flagging
        boolean fromRag = (ragContext != null && !ragContext.isBlank());
        boolean fromWiki = (wikiContext != null && !wikiContext.isBlank());

        String combinedContext;
        String sourceFlag;

        if (fromRag && fromWiki) {
            combinedContext = "Local RAG Knowledge:\n" + ragContext + "\n\nReference Material:\n" + wikiContext;
            String label = wikiTitle != null && wikiTitle.contains("& Live Web Search") 
                    ? "🌐 Wikipedia + 🌐 Live Web Search" 
                    : (wikiTitle != null && wikiTitle.startsWith("Web Search:") ? "🌐 Live Web Search Engine" : "🌐 Wikipedia (" + wikiTitle + ")");
            sourceFlag = "📁 Local RAG Store + " + label;
        } else if (fromWiki) {
            combinedContext = wikiContext;
            String label = wikiTitle != null && wikiTitle.contains("& Live Web Search")
                    ? "🌐 Wikipedia + 🌐 Live Web Search (" + wikiTitle + ")"
                    : (wikiTitle != null && wikiTitle.startsWith("Web Search:") ? "🌐 Live Web Search Engine (" + wikiTitle + ")" : "🌐 Wikipedia (" + wikiTitle + ")");
            sourceFlag = label + " ⚠️ [Note: Not found in uploaded RAG database]";
        } else {
            combinedContext = ragContext;
            sourceFlag = "📁 Local RAG Vector Store (Uploaded Document)";
        }

        String aiSynthesizedAnswer = synthesizeEmergingAiAnswer(question, combinedContext, sourceFlag, wikiUrl);

        return Map.of(
            "question", question,
            "answer", aiSynthesizedAnswer,
            "verified", true,
            "fromRag", fromRag,
            "fromWiki", fromWiki,
            "source", sourceFlag,
            "sourceUrl", wikiUrl != null ? wikiUrl : "",
            "contextUsed", combinedContext
        );
    }

    private String synthesizeEmergingAiAnswer(String question, String rawContext, String sourceFlag, String sourceUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("Answer based on retrieved reference material:\n\n");

        if (rawContext.contains("Local RAG Knowledge:") && rawContext.contains("Wikipedia Reference:")) {
            String[] parts = rawContext.split("Wikipedia Reference:");
            String ragPart = parts[0].replace("Local RAG Knowledge:", "").trim();
            String wikiPart = parts.length > 1 ? parts[1].trim() : "";

            sb.append("📁 **Local RAG Database Result:**\n");
            sb.append(formatConciseSection(ragPart, question)).append("\n\n");

            sb.append("🌐 **Wikipedia Reference Result:**\n");
            sb.append(formatConciseSection(wikiPart, question)).append("\n\n");
        } else {
            sb.append(formatConciseSection(rawContext, question)).append("\n\n");
        }

        sb.append("\nSource: ").append(sourceFlag);
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            sb.append("\nSource URL: ").append(sourceUrl);
        }
        sb.append("\nVerification: Reference material was found and displayed. Review the source before treating the answer as definitive.");

        return sb.toString();
    }

    private String formatConciseSection(String text, String question) {
        if (text == null || text.isBlank()) return "No specific details found.";

        String relevantText = Arrays.stream(text.split("\\n|(?<=[.!?])\\s+"))
            .map(sentence -> sentence == null ? "" : sentence.trim())
                .filter(sentence -> !sentence.isBlank())
                .filter(sentence -> containsSharedWords(question, sentence))
                .collect(java.util.stream.Collectors.joining("\n"));
        if (!relevantText.isBlank()) {
            text = relevantText;
        }
        
        // If it's tabular markdown data (e.g. medicine table), extract direct medicine names and symptoms
        if (text.contains("|")) {
            List<String> lines = Arrays.asList(text.split("\n"));
            List<String> medicines = new ArrayList<>();
            for (String line : lines) {
                if (line.contains("|") && !line.contains("Medicine Name") && !line.contains("---")) {
                    String[] cols = line.split("\\|");
                    if (cols.length >= 3) {
                        String name = cols[2].replaceAll("\\*\\*", "").trim();
                        String symptom = cols.length >= 4 ? cols[3].replaceAll("\\*\\*", "").trim() : "";
                        if (!name.isBlank()) {
                            medicines.add("• **" + name + "**" + (!symptom.isBlank() ? " — " + symptom : ""));
                        }
                    }
                }
            }
            if (!medicines.isEmpty()) {
                return "Matching Medicines & Usage:\n" + String.join("\n", medicines.subList(0, Math.min(medicines.size(), 8)));
            }
        }

        // Clean out textbook headers/author credentials from raw text
        String cleaned = text.replaceAll("(?i)(PROF\\.\\s*&\\s*HEAD|DEPT\\.\\s*OF\\·MEDICINE|KGMC|LUCKNOW|MBBS|MRCP|FRCP|FICP|NOSISA|SYSTEM OF DIA IN OUTLINE).*", "").trim();
        if (cleaned.length() < 20) {
            cleaned = text;
        }

        String[] sentences = text.split("\n|(?<=[.!?])\\s+");
        List<String> bulletFacts = new ArrayList<>();
        for (String s : sentences) {
            String trimmed = s.trim();
            String lower = trimmed.toLowerCase();
            if (trimmed.length() > 15 
                    && !trimmed.startsWith("Local RAG") 
                    && !trimmed.startsWith("Wikipedia Reference")
                    && !lower.contains("ashok chandra")
                    && !lower.contains("kgmc")
                    && !lower.contains("emeritus")
                    && !lower.contains("clinical medicine a system of diagnosis")
                    && !lower.contains("nosisa")
                    && !lower.contains("comprehensive 50-medicine")) {
                bulletFacts.add("• " + trimmed);
            }
        }

        if (!bulletFacts.isEmpty()) {
            return String.join("\n", bulletFacts.subList(0, Math.min(bulletFacts.size(), 6)));
        }

        return cleaned;
    }

    private String resolveContext(AuditRequest request) {
        String mode = request.contextSourceMode() != null ? request.contextSourceMode().toLowerCase().trim() : "auto";
        boolean hasBoxContext = (request.groundTruthContext() != null 
                && !request.groundTruthContext().isBlank()
                && !request.groundTruthContext().equals(ProjectContext.DEFAULT_GROUND_TRUTH_CONTEXT));

        // 1. Strict Ground Truth Input Box Only Mode
        if ("box_only".equals(mode)) {
            return hasBoxContext ? request.groundTruthContext() : null;
        }

        // 2. Local Vector RAG Only Mode
        if ("rag_only".equals(mode)) {
            if (hasBoxContext) return request.groundTruthContext();
            return (localVectorRagService != null) ? localVectorRagService.findRelevantContext(request.generatedText()) : null;
        }

        // 3. Live Wikipedia Only Mode
        if ("wiki_only".equals(mode)) {
            if (hasBoxContext) return request.groundTruthContext();
            return fetchWikipediaContext(request.generatedText());
        }

        // 4. Auto mode: use supplied context, then supplement it once with Wikipedia.
        if (hasBoxContext) {
            String wikipediaContext = fetchWikipediaContext(request.generatedText());
            return combineContexts(request.groundTruthContext(), wikipediaContext);
        }

        if (localVectorRagService != null) {
            String ragContext = localVectorRagService.findRelevantContext(request.generatedText());
            if (ragContext != null && !ragContext.isBlank()) {
                return ragContext;
            }
        }

        return fetchWikipediaContext(request.generatedText());
    }

    private String combineContexts(String primaryContext, String supplementalContext) {
        boolean hasPrimary = primaryContext != null && !primaryContext.isBlank();
        boolean hasSupplemental = supplementalContext != null && !supplementalContext.isBlank();
        if (hasPrimary && hasSupplemental) {
            return primaryContext + "\n\nWikipedia supplemental reference:\n" + supplementalContext;
        }
        if (hasPrimary) {
            return primaryContext;
        }
        return hasSupplemental ? supplementalContext : null;
    }

    private String fetchWikipediaContext(String text) {
        try {
            String topic = extractTopic(text);
            String summary = null;
            com.hallucination.audit.dto.WikipediaSearchResponse searchResp = wikiService.search(topic);
            if (searchResp != null && searchResp.summary() != null) {
                summary = searchResp.summary().trim();
            }

            if (summary != null && (text == null || containsSharedWords(text, summary))) {
                return summary;
            }

            // A model-generated topic can be too broad or unrelated for long claims.
            // Retry with the original text so live Wikipedia mode still gets evidence.
            if (text != null && !text.isBlank() && !text.equalsIgnoreCase(topic)) {
                searchResp = wikiService.search(text);
                if (searchResp != null && searchResp.summary() != null) {
                    return searchResp.summary().trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private double overlapScore(List<String> leftContent, List<String> rightContent) {
        if (leftContent.isEmpty() || rightContent.isEmpty()) {
            return 0.0;
        }
        int matches = 0;
        for (String leftWord : leftContent) {
            for (String rightWord : rightContent) {
                if (leftWord.equals(rightWord) || leftWord.contains(rightWord) || rightWord.contains(leftWord)) {
                    matches++;
                    break;
                }
            }
        }
        return (double) matches / leftContent.size();
    }

    private String extractTopic(String generatedText) {
        try {
            String topic = topicExtractor.extractTopic(generatedText);
            if (topic != null && !topic.isBlank()) {
                return topic.trim();
            }
        } catch (Exception e) {
            // Fallback for offline mode or API issues
        }
        return LocalNlpUtils.extractTopicLocally(generatedText);
    }

    private List<String> normalizeClaims(List<String> rawClaims) {
        return rawClaims.stream()
                .map(claim -> claim == null ? "" : claim.trim())
                .filter(claim -> !claim.isBlank())
                .distinct()
                .toList();
    }

    private List<ClaimEvaluation> evaluateClaimsInParallel(List<String> claims, String groundTruthContext) {
        String effectiveContext = groundTruthContext;
        List<String> safeClaims = claims == null ? List.of() : claims;
        
        // Optimize: If context is missing, fetch a single shared Wikipedia context for the entire batch 
        // to prevent API rate limits (429) and timeouts from parallel Wikipedia queries.
        if ((effectiveContext == null || effectiveContext.isBlank() || effectiveContext.equals(ProjectContext.DEFAULT_GROUND_TRUTH_CONTEXT)) && !safeClaims.isEmpty()) {
            try {
                com.hallucination.audit.dto.WikipediaSearchResponse wikiResp = wikiService.search(extractTopic(safeClaims.get(0)));
                if (wikiResp != null && wikiResp.summary() != null && wikiResp.summary().length() > 50) {
                    effectiveContext = wikiResp.summary().trim();
                }
            } catch (Exception ignored) {}
        }
        
        final String finalContext = (effectiveContext != null && !effectiveContext.isBlank()) ? effectiveContext : ProjectContext.DEFAULT_GROUND_TRUTH_CONTEXT;

        List<CompletableFuture<ClaimEvaluation>> futures = safeClaims.stream()
            .limit(20) // Cap to max 20 claims for long inputs to prevent timeouts
            .map(claim -> {
                String safeClaim = java.util.Objects.requireNonNull(claim, "Claim must not be null");
                return CompletableFuture.supplyAsync(
                    () -> evaluateClaim(safeClaim, finalContext),
                    auditExecutor
                )
                // Increased timeout to 12s to accommodate LLM evaluations for long contexts
                .orTimeout(12000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .exceptionally(ex -> fallbackEvaluation(safeClaim, finalContext));
            })
            .toList();

        return futures.stream()
            .map(f -> f.join())
            .toList();
    }

    private ClaimEvaluation evaluateClaim(@NonNull String claim, @NonNull String groundTruthContext) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return fallbackEvaluation(claim, groundTruthContext);
        }
        try {
            String effectiveContext = groundTruthContext;
            List<com.hallucination.audit.dto.WikipediaSearchResponse> evidence = List.of();
            
            // If user did not provide context, we MUST fetch it before evaluating via LLM!
            if (groundTruthContext == null || groundTruthContext.isBlank() || groundTruthContext.equals(ProjectContext.DEFAULT_GROUND_TRUTH_CONTEXT)) {
                evidence = gatherEvidenceForClaim(claim, groundTruthContext);
                if (evidence != null && !evidence.isEmpty() && evidence.get(0).summary() != null) {
                    effectiveContext = evidence.get(0).summary();
                } else {
                    return new ClaimEvaluation(claim, NliLabel.NEUTRAL, "No Wikipedia or reference context was found to evaluate this claim.", null, List.of());
                }
            }

            ClaimEvaluation evaluation = hallucinationAuditor.evaluate(claim, effectiveContext);
            if (evaluation == null) {
                return new ClaimEvaluation(claim, NliLabel.NEUTRAL, "No evaluation returned by auditor.", null, evidence);
            }

            // Attach any evidence returned by the auditor, otherwise use our fetched evidence.
            List<com.hallucination.audit.dto.WikipediaSearchResponse> finalEvidence = evaluation.evidence() == null || evaluation.evidence().isEmpty()
                ? evidence
                : evaluation.evidence();
            if (finalEvidence == null) {
                finalEvidence = List.of();
            }
            
            if (finalEvidence.isEmpty()) {
                finalEvidence = gatherEvidenceForClaim(claim, effectiveContext);
            }

            return new ClaimEvaluation(
                claim,
                evaluation.label() == null ? NliLabel.NEUTRAL : evaluation.label(),
                evaluation.reasoning() == null ? "No reasoning provided." : evaluation.reasoning(),
                evaluation.contradictingEvidenceSnippet(),
                finalEvidence == null ? List.of() : finalEvidence
            );
        } catch (Exception exception) {
            return fallbackEvaluation(claim, groundTruthContext);
        }
    }

    private ClaimEvaluation fallbackEvaluation(String claim, String groundTruthContext) {
        String normalizedClaim = claim == null ? "" : claim.trim().toLowerCase();
        String normalizedContext = groundTruthContext == null ? "" : groundTruthContext.trim().toLowerCase();

        if (normalizedClaim.isBlank()) {
            return new ClaimEvaluation(claim, NliLabel.NEUTRAL, "No evaluable claim was provided.", null, List.of());
        }

        if (normalizedContext.isBlank()) {
            try {
                String topic = extractTopic(claim);
                if (topic != null && !topic.isBlank()) {
                    com.hallucination.audit.dto.WikipediaSearchResponse wikiResp = wikiService.search(topic);
                    if (wikiResp != null && wikiResp.summary() != null && wikiResp.summary().length() > 50) {
                        String wikiSummary = wikiResp.summary();
                        boolean wikiContradiction = looksLikeContradiction(claim, wikiSummary);
                        if (wikiContradiction) {
                            String snippet = findBestMatchingSentence(claim, wikiSummary);
                            return new ClaimEvaluation(claim, NliLabel.CONTRADICTION, "The claim contradicts external Wikipedia facts.", snippet, List.of(wikiResp));
                        }
                        if (containsSharedWords(normalizedClaim, wikiSummary.toLowerCase())) {
                            return new ClaimEvaluation(claim, NliLabel.ENTAILMENT, "Verified via external Wikipedia facts.", null, List.of(wikiResp));
                        }
                    }
                }
            } catch (Exception ignored) {}
            List<com.hallucination.audit.dto.WikipediaSearchResponse> evidence = gatherEvidenceForClaim(claim, groundTruthContext);
            return new ClaimEvaluation(claim, NliLabel.NEUTRAL, "No context was available, so the claim could not be verified.", null, evidence);
        }

        List<com.hallucination.audit.dto.WikipediaSearchResponse> evidence = gatherEvidenceForClaim(claim, groundTruthContext);

        // Check for contradictions FIRST to avoid false entailments on overlapping keywords
        boolean looksContradictory = looksLikeContradiction(claim, groundTruthContext);
        if (looksContradictory) {
            String detail = buildContradictionDetail(claim, groundTruthContext);
            String snippet = findBestMatchingSentence(claim, groundTruthContext);
            return new ClaimEvaluation(claim, NliLabel.CONTRADICTION, detail, snippet, evidence);
        }

        boolean contextMentionsTopic = containsSharedWords(normalizedClaim, normalizedContext);
        if (contextMentionsTopic) {
            return new ClaimEvaluation(claim, NliLabel.ENTAILMENT, "The claim is supported by facts in the reference context.", null, evidence);
        }

        // If local context lacks details for a specific claim (e.g. Girnar), query Wikipedia dynamically to verify!
        try {
            String topic = extractTopic(claim);
            if (topic != null && !topic.isBlank()) {
                com.hallucination.audit.dto.WikipediaSearchResponse wikiResp = wikiService.search(topic);
                if (wikiResp != null && wikiResp.summary() != null && wikiResp.summary().length() > 80) {
                    String wikiSummary = wikiResp.summary();
                    boolean wikiContradiction = looksLikeContradiction(claim, wikiSummary);
                    if (wikiContradiction) {
                        String snippet = findBestMatchingSentence(claim, wikiSummary);
                        return new ClaimEvaluation(claim, NliLabel.CONTRADICTION, "The claim contradicts external Wikipedia facts.", snippet, List.of(wikiResp));
                    }
                    if (containsSharedWords(normalizedClaim, wikiSummary.toLowerCase())) {
                        return new ClaimEvaluation(claim, NliLabel.ENTAILMENT, "Verified via external Wikipedia facts.", null, List.of(wikiResp));
                    }
                }
            }
        } catch (Exception e) {
            // Ignore Wikipedia query errors, proceed to return neutral
        }

        return new ClaimEvaluation(claim, NliLabel.NEUTRAL, "The available context does not contain sufficient details to verify this claim.", null, evidence);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hallucination.audit.ai.SubQueryExtractor subQueryExtractor;

    private List<com.hallucination.audit.dto.WikipediaSearchResponse> gatherEvidenceForClaim(String claim, String context) {
        if (context != null && !context.isBlank() && !context.equals(ProjectContext.DEFAULT_GROUND_TRUTH_CONTEXT)) {
            // Find specific matching sentence in groundTruthContext if possible
            String matchingSentence = findBestMatchingSentence(claim, context);
            String summary = (matchingSentence != null && !matchingSentence.isBlank()) 
                    ? matchingSentence 
                    : (context.substring(0, Math.min(context.length(), 220)) + "...");
            return List.of(new com.hallucination.audit.dto.WikipediaSearchResponse("Ground Truth Context Reference", null, summary));
        }
        try {
            String primaryTopic = extractTopic(claim);
            List<String> queries = new ArrayList<>();
            if (primaryTopic != null && !primaryTopic.isBlank()) {
                queries.add(primaryTopic);
            }
            if (subQueryExtractor != null) {
                try {
                    List<String> subQueries = subQueryExtractor.extractSubQueries(claim);
                    if (subQueries != null) {
                        for (String sq : subQueries) {
                            if (sq != null && !sq.isBlank() && !queries.contains(sq)) {
                                queries.add(sq.trim());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (queries.isEmpty() && claim != null) {
                queries.add(claim);
            }

            com.hallucination.audit.dto.WikipediaSearchResponse resp = wikiService.searchMultipleParallel(queries);
            if (resp == null || resp.summary() == null || resp.summary().isBlank() || resp.summary().length() <= 50) {
                return List.of();
            }
            return List.of(resp);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String findBestMatchingSentence(String claim, String context) {
        if (claim == null || context == null) return null;
        String[] sentences = context.split("(?<=[.!?])\\s+");
        String bestSentence = null;
        double bestScore = 0.0;
        
        List<String> claimTokens = LocalNlpUtils.tokenizeAndClean(claim);
        if (claimTokens.isEmpty()) return null;

        for (String s : sentences) {
            List<String> sentenceTokens = LocalNlpUtils.tokenizeAndClean(s);
            double score = overlapScore(claimTokens, sentenceTokens);
            if (score > bestScore && score >= 0.15) {
                bestScore = score;
                bestSentence = s.trim();
            }
        }
        return bestSentence;
    }

    private boolean containsSharedWords(String claim, String context) {
        if (claim == null || context == null || claim.isBlank() || context.isBlank()) {
            return false;
        }

        List<String> claimTokens = LocalNlpUtils.tokenizeAndClean(claim);
        List<String> contextTokens = LocalNlpUtils.tokenizeAndClean(context);
        double score = overlapScore(claimTokens, contextTokens);
        return score >= 0.15;
    }


    private String buildContradictionDetail(String claim, String context) {
        if (claim == null || context == null) return "The claim contradicts facts in the reference context.";

        String matchingContext = findBestMatchingSentence(claim, context);
        String relevantContext = matchingContext == null ? context : matchingContext;
        if (hasNegationConflict(claim, relevantContext)) {
            boolean claimNegated = containsNegation(claim);
            return "Negation Conflict: The claim says "
                    + (claimNegated ? "the statement is false or did not happen" : "the statement happened")
                    + ", but the reference says "
                    + (claimNegated ? "it happened" : "it did not happen")
                    + ". Evidence: \"" + relevantContext.trim() + "\"";
        }

        java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("\\b(\\d+(?:\\.\\d+)?|\\d+%|\\d+-\\d+)\\b");
        java.util.regex.Matcher matcher = numPattern.matcher(claim);
        while (matcher.find()) {
            String num = matcher.group(1);
            if (!relevantContext.contains(num)) {
                java.util.regex.Matcher ctxMatcher = numPattern.matcher(relevantContext);
                while (ctxMatcher.find()) {
                    String ctxNum = ctxMatcher.group(1);
                    if (isSameNumericType(num, ctxNum) && !num.equals(ctxNum)) {
                        return "Numeric Mismatch: The claim states '" + num
                                + "', but the matching reference sentence states '" + ctxNum
                                + "'. Evidence: \"" + relevantContext.trim() + "\"";
                    }
                }
            }
        }
        return "Factual Contradiction: The claim conflicts with the matching reference sentence. Evidence: \""
                + relevantContext.trim() + "\"";
    }

    private boolean containsNegation(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String[] negationWords = {"not", "never", "no", "denied", "refused", "incorrect", "false", "fail"};
        String normalized = text.toLowerCase();
        for (String negationWord : negationWords) {
            if (java.util.regex.Pattern.compile("\\b" + negationWord + "\\b").matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeContradiction(String claim, String context) {
        if (claim == null || context == null || claim.isBlank() || context.isBlank()) {
            return false;
        }

        String matchingContext = findBestMatchingSentence(claim, context);
        if (matchingContext == null) {
            return false;
        }

        // 1. Semantic negation polarity conflict in the relevant context sentence
        if (hasNegationConflict(claim, matchingContext)) {
            return true;
        }

        // 2. Mismatching numeric/statistical values. An absent number alone is
        // unsupported evidence, not a contradiction; a competing number is required.
        if (hasNumericMismatch(claim.toLowerCase(), matchingContext.toLowerCase())) {
            return true;
        }

        return false;
    }

    private boolean hasNegationConflict(String claim, String context) {
        String c = claim == null ? "" : claim.toLowerCase();
        String cx = context == null ? "" : context.toLowerCase();
        
        // Filter out non-negating idiom phrases like 'no longer', 'no more', 'no doubt', 'no matter'
        String cleanedCx = cx.replaceAll("\\bno\\s+(longer|more|doubt|matter|less)\\b", " ");
        String cleanedC = c.replaceAll("\\bno\\s+(longer|more|doubt|matter|less)\\b", " ");

        String[] negationWords = {"not", "never", "no", "denied", "refused", "incorrect", "false", "fail", "cannot", "isnt", "wasnt", "doesnt"};
        
        boolean claimHasNegation = false;
        for (String nw : negationWords) {
            if (java.util.regex.Pattern.compile("\\b" + nw + "\\b").matcher(cleanedC).find()) {
                claimHasNegation = true;
                break;
            }
        }
        
        boolean contextHasNegation = false;
        for (String nw : negationWords) {
            if (java.util.regex.Pattern.compile("\\b" + nw + "\\b").matcher(cleanedCx).find()) {
                contextHasNegation = true;
                break;
            }
        }

        List<String> claimTokens = LocalNlpUtils.tokenizeAndClean(c);
        List<String> contextTokens = LocalNlpUtils.tokenizeAndClean(cx);
        double overlap = overlapScore(claimTokens, contextTokens);

        return (claimHasNegation != contextHasNegation) && overlap >= 0.35;
    }

    private boolean hasNumericMismatch(String claim, String context) {
        java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("\\b(\\d+(?:\\.\\d+)?|\\d+%|\\d+-\\d+)\\b");
        java.util.regex.Matcher matcher = numPattern.matcher(claim);
        
        boolean foundMismatch = false;
        while (matcher.find()) {
            String num = matcher.group(1);
            if (!context.contains(num)) {
                java.util.regex.Matcher contextMatcher = numPattern.matcher(context);
                while (contextMatcher.find()) {
                    String contextNumber = contextMatcher.group(1);
                    if (isSameNumericType(num, contextNumber) && !num.equals(contextNumber)) {
                        foundMismatch = true;
                        break;
                    }
                }
            }
            if (foundMismatch) break;
        }
        return foundMismatch && containsSharedWords(claim, context);
    }

    private boolean isSameNumericType(String n1, String n2) {
        if (n1.endsWith("%") && n2.endsWith("%")) return true;
        if (n1.contains("-") && n2.contains("-")) return true;
        boolean isN1Num = n1.matches("\\d+(?:\\.\\d+)?");
        boolean isN2Num = n2.matches("\\d+(?:\\.\\d+)?");
        return isN1Num && isN2Num;
    }

    private AuditResult buildAuditResult(List<ClaimEvaluation> evaluations, List<ExtractedCitation> checkedCitations) {
        int entailmentCount = 0;
        int neutralCount = 0;
        int contradictionCount = 0;
        List<DetectedContradiction> flaggedClaims = new ArrayList<>();

        for (ClaimEvaluation evaluation : evaluations) {
            switch (evaluation.label()) {
                case ENTAILMENT -> entailmentCount++;
                case NEUTRAL -> neutralCount++;
                case CONTRADICTION -> {
                    contradictionCount++;
                    flaggedClaims.add(toDetectedContradiction(evaluation));
                }
            }
        }

        long hallucinatedCitationsCount = checkedCitations.stream()
                .filter(c -> "HALLUCINATED".equalsIgnoreCase(c.status()))
                .count();

        double claimRiskScore = evaluations.isEmpty()
            ? 0.0
            : ((contradictionCount + (neutralCount * neutralWeight)) / (double) evaluations.size()) * 100.0;
        double citationRiskScore = checkedCitations.isEmpty()
            ? 0.0
            : ((double) hallucinatedCitationsCount / checkedCitations.size()) * 50.0;
        double hallucinationScore = roundScore(Math.min(100.0, Math.max(claimRiskScore, citationRiskScore)));

        AuditStatus status = (contradictionCount > 0 || hallucinationScore >= flagThreshold || hallucinatedCitationsCount > 0)
                ? AuditStatus.FLAGGED
                : AuditStatus.SAFE;

        String reasoning = buildReasoningSummary(
                evaluations.size(),
                entailmentCount,
                neutralCount,
                contradictionCount,
                (int) hallucinatedCitationsCount,
                hallucinationScore,
                status
        );

        String userFriendlyMessage = buildUserFriendlyMessage(hallucinationScore, status, contradictionCount, (int) hallucinatedCitationsCount);
        String finalReasoning = reasoning + " " + userFriendlyMessage;

        return new AuditResult(hallucinationScore, status, finalReasoning, List.copyOf(flaggedClaims), List.copyOf(evaluations), checkedCitations);
    }


    private DetectedContradiction toDetectedContradiction(ClaimEvaluation evaluation) {
        return new DetectedContradiction(
                evaluation.claim(),
                evaluation.label(),
                evaluation.reasoning(),
                evaluation.evidence() == null ? List.of() : evaluation.evidence()
        );
    }

    private String buildReasoningSummary(
            int totalClaims,
            int entailmentCount,
            int neutralCount,
            int contradictionCount,
            int hallucinatedCitations,
            double hallucinationScore,
            AuditStatus status
    ) {
        return "Evaluated %d claims (%d ENTAILMENT, %d NEUTRAL, %d CONTRADICTION) and %d citations. "
                .formatted(totalClaims, entailmentCount, neutralCount, contradictionCount, hallucinatedCitations)
                + "Hallucination score: %.1f/100.0. Status: %s."
                .formatted(hallucinationScore, status);
    }

    private double roundScore(double score) {
        return Math.round(score * 10.0) / 10.0;
    }

    private String buildUserFriendlyMessage(double hallucinationScore, AuditStatus status, int contradictionCount, int hallucinatedCitations) {
        StringBuilder sb = new StringBuilder();
        if (hallucinatedCitations > 0) {
            sb.append("⚠️ Hallucination Warning: Detected ").append(hallucinatedCitations)
              .append(" fabricated citation marker(s) ('vibe citations') in text. ");
        }
        if (contradictionCount > 0) {
            sb.append("❌ Critical Error: Found ").append(contradictionCount)
              .append(" factual contradiction(s) / numeric mismatch(es) against ground truth. ");
        }
        if (status == AuditStatus.FLAGGED) {
            sb.append("Audit Status: FLAGGED (Estimated Hallucination Risk: ")
              .append(String.format("%.1f%%", hallucinationScore)).append(").");
        } else if (hallucinationScore >= 20.0) {
            sb.append("Audit Status: CAUTION (Estimated Risk: ")
              .append(String.format("%.1f%%", hallucinationScore)).append(").");
        } else {
            sb.append("Audit Status: SAFE (Grounding Verified, Risk: ")
              .append(String.format("%.1f%%", hallucinationScore)).append(").");
        }
        return sb.toString();
    }

    private void persistAuditLog(AuditRequest request, AuditResult result) {
        try {
            String contradictionsJson = objectMapper.writeValueAsString(result.detectedContradictions());
            String citationsJson = objectMapper.writeValueAsString(result.checkedCitations());
            String effectiveCtx = (request.groundTruthContext() != null) ? request.groundTruthContext() : "";
            auditLogRepository.save(new AuditLogEntity(
                    request.generatedText(),
                    effectiveCtx,
                    result.hallucinationScore(),
                    result.status(),
                    result.reasoning(),
                    contradictionsJson,
                    citationsJson
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize detected contradictions/citations for persistence", exception);
        }
    }


}
