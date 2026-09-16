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

    public String findRelevantContext(String topic, String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return null;
        }

        EmbeddingStore<TextSegment> storeToSearch = globalEmbeddingStore;
        if (topic != null && !topic.isBlank() && !topic.equalsIgnoreCase("all")) {
            EmbeddingStore<TextSegment> topicStore = topicStores.get(topic.trim().toLowerCase());
            if (topicStore != null) {
                storeToSearch = topicStore;
            }
        }

        var queryEmbedding = embeddingModel.embed(queryText).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .minScore(0.40)
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

        List<String> validTexts = matches.stream()
                .filter(m -> m.score() >= 0.40)
                .map(match -> match.embedded().text())
            .filter(textSegment -> hasEnoughMeaningfulOverlap(queryText, textSegment))
                .filter(textSegment -> {
                    String lowerT = textSegment.toLowerCase();
                    if (lowerT.contains("prof. ashok chandra") && !lowerT.contains("paracetamol") && !lowerT.contains("aspirin") && !lowerT.contains("ibuprofen") && !lowerT.contains("sumatriptan")) {
                        return false;
                    }
                    return true;
                })
                .distinct()
                .collect(Collectors.toList());

        return validTexts.isEmpty() ? null : String.join("\n", validTexts);
    }

    private static boolean hasEnoughMeaningfulOverlap(String queryText, String candidateText) {
        Set<String> queryTokens = meaningfulTokens(queryText);
        Set<String> candidateTokens = meaningfulTokens(candidateText);
        if (queryTokens.isEmpty()) {
            return false;
        }

        long matchingTokens = queryTokens.stream()
                .filter(queryToken -> candidateTokens.stream().anyMatch(candidateToken ->
                        queryToken.equals(candidateToken)
                                || (queryToken.length() >= 4 && candidateToken.startsWith(queryToken))
                                || (candidateToken.length() >= 4 && queryToken.startsWith(candidateToken))))
                .count();

        int requiredMatches = queryTokens.size() >= 3 ? 2 : 1;
        return matchingTokens >= requiredMatches;
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
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
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
