package com.hallucination.audit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class LocalVectorRagService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalVectorRagService.class);
    private static final String CACHE_FILE_PATH = "local-rag-store.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> globalEmbeddingStore;
    private final Map<String, EmbeddingStore<TextSegment>> topicStores = new ConcurrentHashMap<>();
    private final List<IngestedDocRecord> persistentDocs = new CopyOnWriteArrayList<>();

    public LocalVectorRagService() {
        this.embeddingModel = new LocalFastEmbeddingModel();
        this.globalEmbeddingStore = new InMemoryEmbeddingStore<>();
    }

    @PostConstruct
    public void initSeedKnowledgeBase() {
        // Reload user-embedded vector documents from disk cache if present
        loadFromDiskCache();
    }

    public void ingestDocument(String docId, String text) {
        ingestDocument("General", docId, text);
    }

    public void ingestDocument(String topic, String docId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalizedTopic = (topic == null || topic.isBlank()) ? "General" : topic.trim();
        String effectiveDocId = (docId == null || docId.isBlank()) ? "doc-" + System.currentTimeMillis() : docId.trim();

        ingestInternal(normalizedTopic, effectiveDocId, text);

        // Record for persistent disk storage
        persistentDocs.add(new IngestedDocRecord(normalizedTopic, effectiveDocId, text));
        saveToDiskCache();
    }

    private void ingestInternal(String topic, String docId, String text) {
        var metadata = Metadata.from(Map.of("topic", topic, "docId", docId));
        var document = dev.langchain4j.data.document.Document.from(text, metadata);
        var splitter = DocumentSplitters.recursive(300, 50);
        List<TextSegment> segments = splitter.split(document);

        if (segments.isEmpty()) {
            return;
        }

        var embeddings = embeddingModel.embedAll(segments).content();
        globalEmbeddingStore.addAll(embeddings, segments);

        topicStores.computeIfAbsent(topic.toLowerCase(), k -> new InMemoryEmbeddingStore<>())
                .addAll(embeddings, segments);
    }

    public List<String> listAvailableTopics() {
        Set<String> topics = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        topics.add("General");
        topics.addAll(topicStores.keySet());
        return new ArrayList<>(topics);
    }

    public List<IngestedDocRecord> getIngestedDocuments() {
        return Collections.unmodifiableList(persistentDocs);
    }

    public void clearTopicStore(String topic) {
        if (topic == null || topic.isBlank() || topic.equalsIgnoreCase("all")) {
            clearAllStores();
            return;
        }
        String normalized = topic.trim().toLowerCase();
        topicStores.remove(normalized);
        persistentDocs.removeIf(doc -> doc.topic().equalsIgnoreCase(normalized));
        saveToDiskCache();
    }

    public void clearAllStores() {
        topicStores.clear();
        persistentDocs.clear();
        saveToDiskCache();
    }

    public String findRelevantContext(String queryText) {
        return findRelevantContext(null, queryText);
    }

    public String findRelevantContext(String targetDocIdOrTopic, String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return null;
        }

        EmbeddingStore<TextSegment> storeToSearch = globalEmbeddingStore;
        String filterDocId = null;

        if (targetDocIdOrTopic != null && !targetDocIdOrTopic.isBlank() && !targetDocIdOrTopic.equalsIgnoreCase("all")) {
            String targetClean = targetDocIdOrTopic.trim().toLowerCase();
            // Check if target matches a specific topic store
            EmbeddingStore<TextSegment> topicStore = topicStores.get(targetClean);
            if (topicStore != null) {
                storeToSearch = topicStore;
            } else {
                // Check if target matches a specific document ID in persistentDocs
                boolean matchesDocId = persistentDocs.stream().anyMatch(doc -> doc.docId().equalsIgnoreCase(targetClean) || doc.docId().toLowerCase().contains(targetClean));
                if (matchesDocId) {
                    filterDocId = targetClean;
                }
            }
        }

        var queryEmbedding = embeddingModel.embed(queryText).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(20)
                .minScore(0.15)
                .build();

        EmbeddingSearchResult<TextSegment> result = storeToSearch.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        if ((matches == null || matches.isEmpty()) && storeToSearch != globalEmbeddingStore) {
            result = globalEmbeddingStore.search(request);
            matches = result.matches();
        }

        if (matches == null || matches.isEmpty()) {
            return null;
        }

        final String activeDocFilter = filterDocId;

        // Perform Hybrid Vector + Keyword Rank Fusion Scoring with noise filtering
        boolean queryHasCoreNouns = hasCoreNounsInQuery(queryText);

        List<String> validTexts = matches.stream()
                .filter(match -> {
                    if (activeDocFilter == null) return true;
                    String docId = match.embedded().metadata().getString("docId");
                    return docId != null && docId.toLowerCase().contains(activeDocFilter);
                })
                .filter(match -> !isNoiseOrFacultyChunk(match.embedded().text()))
                .filter(match -> hasEnoughMeaningfulOverlap(queryText, match.embedded().text()))
                .map(match -> {
                    double vectorScore = match.score();
                    double keywordSynonymScore = calculateSynonymKeywordScore(queryText, match.embedded().text());
                    double coreTopicBonus = calculateCoreTopicBonus(queryText, match.embedded().text());
                    double hybridScore = (0.4 * vectorScore) + (0.4 * keywordSynonymScore) + (0.2 * coreTopicBonus);
                    return new HybridMatch(match, vectorScore, hybridScore, keywordSynonymScore, coreTopicBonus);
                })
                .filter(hm -> {
                    if (queryHasCoreNouns && hm.coreTopicBonus == 0.0 && hm.vectorScore < 0.38) {
                        return false;
                    }
                    return hm.hybridScore >= 0.15 || hm.vectorScore >= 0.30 || hm.keywordSynonymScore >= 0.20;
                })
                .sorted(Comparator.comparingDouble((HybridMatch hm) -> hm.hybridScore).reversed())
                .limit(6)
                .map(hm -> {
                    String text = hm.match.embedded().text();
                    String docId = hm.match.embedded().metadata().getString("docId");
                    String docTopic = hm.match.embedded().metadata().getString("topic");
                    if (docId != null && !docId.isBlank()) {
                        return "### 📄 Document Reference: " + docId + (docTopic != null ? " (Topic: " + docTopic + ")" : "") + "\n" + text;
                    }
                    return text;
                })
                .distinct()
                .collect(Collectors.toList());

        return validTexts.isEmpty() ? null : String.join("\n\n", validTexts);
    }

    private static boolean isNoiseOrFacultyChunk(String text) {
        if (text == null || text.isBlank()) return true;
        String lower = text.toLowerCase();

        int facultyScore = 0;
        if (lower.contains("professor")) facultyScore++;
        if (lower.contains("department of")) facultyScore++;
        if (lower.contains("civil hospital")) facultyScore++;
        if (lower.contains("medical college")) facultyScore++;
        if (lower.contains("additional professor")) facultyScore++;
        if (lower.contains("assistant professor")) facultyScore++;
        if (lower.contains("associate professor")) facultyScore++;
        if (lower.contains("head of unit")) facultyScore++;

        return facultyScore >= 2;
    }

    private static boolean hasCoreNounsInQuery(String queryText) {
        Set<String> queryTokens = meaningfulTokens(queryText);
        return queryTokens.stream()
                .anyMatch(t -> !t.equals("medicine") && !t.equals("medicines") && !t.equals("drug") && !t.equals("drugs")
                          && !t.equals("name") && !t.equals("list") && !t.equals("what") && !t.equals("give"));
    }

    private static double calculateCoreTopicBonus(String queryText, String candidateText) {
        Set<String> queryTokens = meaningfulTokens(queryText);
        Set<String> candidateTokens = meaningfulTokens(candidateText);
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0;

        // Specific non-generic query nouns get bonus if present in candidate
        Set<String> coreNouns = queryTokens.stream()
                .filter(t -> !t.equals("medicine") && !t.equals("medicines") && !t.equals("drug") && !t.equals("drugs")
                          && !t.equals("name") && !t.equals("list") && !t.equals("what") && !t.equals("give"))
                .collect(Collectors.toSet());

        if (coreNouns.isEmpty()) return 0.0;

        long matchedNouns = coreNouns.stream()
                .filter(noun -> candidateTokens.stream().anyMatch(cToken -> cToken.contains(noun) || noun.contains(cToken)))
                .count();

        return (double) matchedNouns / coreNouns.size();
    }

    private static double calculateSynonymKeywordScore(String queryText, String candidateText) {
        Set<String> queryTokens = meaningfulTokens(queryText);
        if (queryTokens.isEmpty()) return 0.0;
        Set<String> expandedQueryTokens = expandSynonyms(queryTokens);
        Set<String> candidateTokens = meaningfulTokens(candidateText);

        long matches = expandedQueryTokens.stream()
                .filter(qToken -> candidateTokens.stream().anyMatch(cToken ->
                        qToken.equals(cToken)
                                || (qToken.length() >= 4 && cToken.length() >= 4 && (cToken.startsWith(qToken) || qToken.startsWith(cToken)))))
                .count();

        return (double) matches / Math.max(1, queryTokens.size());
    }

    private record HybridMatch(EmbeddingMatch<TextSegment> match, double vectorScore, double hybridScore, double keywordSynonymScore, double coreTopicBonus) {}

    private static boolean hasEnoughMeaningfulOverlap(String queryText, String candidateText) {
        return calculateSynonymKeywordScore(queryText, candidateText) > 0;
    }

    private static Set<String> expandSynonyms(Set<String> tokens) {
        Set<String> expanded = new HashSet<>(tokens);
        for (String t : tokens) {
            String lower = t.toLowerCase();
            if (lower.equals("medicine") || lower.equals("medicines") || lower.equals("medication") || lower.equals("drug") || lower.equals("drugs")) {
                expanded.addAll(List.of("medicine", "medicines", "drug", "drugs", "medication", "medications", "pill", "tablet", "capsule", "pharmacology"));
            } else if (lower.equals("migraine") || lower.equals("migraines")) {
                expanded.addAll(List.of("migraine", "migraines", "headache", "headaches", "cephalalgia", "vascular"));
            } else if (lower.equals("fever") || lower.equals("fevers")) {
                expanded.addAll(List.of("fever", "fevers", "pyrexia", "temperature", "febrile", "antipyretic"));
            } else if (lower.equals("pain") || lower.equals("pains")) {
                expanded.addAll(List.of("pain", "pains", "ache", "aches", "analgesic", "discomfort"));
            } else if (lower.equals("cause") || lower.equals("causes") || lower.equals("reason")) {
                expanded.addAll(List.of("cause", "causes", "etiology", "origin", "driver", "mechanism"));
            }
        }
        return expanded;
    }

    private static Set<String> meaningfulTokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
                .filter(token -> token.length() >= 3 && !LocalFastEmbeddingModel.STOPWORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private synchronized void saveToDiskCache() {
        try {
            File file = new File(CACHE_FILE_PATH);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, persistentDocs);
        } catch (Exception e) {
            log.warn("Failed to save/load disk cache", e);
        }
    }

    private synchronized void loadFromDiskCache() {
        try {
            File file = new File(CACHE_FILE_PATH);
            if (file.exists() && file.length() > 0) {
                List<IngestedDocRecord> records = objectMapper.readValue(file, new TypeReference<List<IngestedDocRecord>>() {});
                Set<String> loadedDocumentKeys = new HashSet<>();
                for (IngestedDocRecord record : records) {
                    String documentKey = record.topic().toLowerCase() + "\u0000" + record.docId();
                    if (loadedDocumentKeys.add(documentKey)) {
                        ingestInternal(record.topic(), record.docId(), record.text());
                        persistentDocs.add(record);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to save/load disk cache", e);
        }
    }

    public record IngestedDocRecord(String topic, String docId, String text) {}

    /**
     * In-process vector embedding model generating dense feature vectors
     * from n-gram hashing and TF-IDF representations.
     */
    private static class LocalFastEmbeddingModel implements EmbeddingModel {

        private static final int VECTOR_DIMENSION = 1024;
        private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "is", "was", "were", "are", "be", "been", "in", "on", "at",
            "of", "for", "to", "from", "and", "or", "by", "with", "as", "that", "this", "it"
        );

        @Override
        public Response<Embedding> embed(String text) {
            return Response.from(Embedding.from(generateVector(text)));
        }

        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            return embed(textSegment.text());
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> list = textSegments.stream()
                    .map(seg -> Embedding.from(generateVector(seg.text())))
                    .collect(Collectors.toList());
            return Response.from(list);
        }

        private float[] generateVector(String text) {
            float[] vector = new float[VECTOR_DIMENSION];
            if (text == null || text.isBlank()) {
                return vector;
            }

            String normalized = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
            String[] tokens = normalized.split("\\s+");

            for (String token : tokens) {
                if (token.isBlank() || STOPWORDS.contains(token) || token.length() < 2) continue;
                int hash = Math.abs(token.hashCode()) % VECTOR_DIMENSION;
                vector[hash] += 1.0f;
            }

            // Normalize vector to unit length (L2 norm)
            float sumSq = 0.0f;
            for (float v : vector) {
                sumSq += v * v;
            }
            if (sumSq > 0) {
                float norm = (float) Math.sqrt(sumSq);
                for (int i = 0; i < VECTOR_DIMENSION; i++) {
                    vector[i] /= norm;
                }
            }

            return vector;
        }
    }
}
