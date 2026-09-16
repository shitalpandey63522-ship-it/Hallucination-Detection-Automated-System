package com.hallucination.audit.controller;

import com.hallucination.audit.service.LocalVectorRagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final LocalVectorRagService localVectorRagService;

    public RagController(LocalVectorRagService localVectorRagService) {
        this.localVectorRagService = localVectorRagService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestKnowledge(
            @RequestParam(defaultValue = "General") String topic,
            @RequestParam(defaultValue = "user-doc") String docId,
            @RequestBody String text
    ) {
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Text must not be empty"));
        }
        localVectorRagService.ingestDocument(topic, docId, text);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Document ingested into topic [" + topic + "] vector store successfully",
                "topic", topic,
                "docId", docId
        ));
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadKnowledgeFile(
            @RequestParam(defaultValue = "General") String topic,
            @RequestParam(required = false) String docId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Uploaded file must not be empty"));
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String effectiveDocId = (docId != null && !docId.isBlank()) 
                    ? docId 
                    : (originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9-]", "-") : "file-doc");

            String extractedText = com.hallucination.audit.util.FileTextExtractor.extractText(file);

            if (extractedText.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Could not extract readable text from file"));
            }

            String safeFilename = (originalFilename != null && !originalFilename.isBlank()) ? originalFilename : "uploaded-file";
            localVectorRagService.ingestDocument(topic, effectiveDocId, extractedText);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "File [" + safeFilename + "] ingested into topic [" + topic + "] vector database successfully!",
                    "topic", topic,
                    "docId", effectiveDocId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "File processing failed: " + e.getMessage()));
        }
    }

    @GetMapping("/topics")
    public ResponseEntity<List<String>> listTopics() {
        return ResponseEntity.ok(localVectorRagService.listAvailableTopics());
    }

    @GetMapping("/documents")
    public ResponseEntity<List<LocalVectorRagService.IngestedDocRecord>> getIngestedDocuments() {
        return ResponseEntity.ok(localVectorRagService.getIngestedDocuments());
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearVectorDatabase(
            @RequestParam(required = false) String topic
    ) {
        localVectorRagService.clearTopicStore(topic);
        String target = (topic == null || topic.isBlank() || topic.equalsIgnoreCase("all")) ? "All Topics" : topic;
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Vector database for [" + target + "] cleared successfully!"
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchKnowledge(
            @RequestParam(required = false) String topic,
            @RequestParam String query
    ) {
        String match = localVectorRagService.findRelevantContext(topic, query);
        return ResponseEntity.ok(Map.of(
                "topic", topic != null ? topic : "All Topics",
                "query", query,
                "matchedContext", match != null ? match : "No vector similarity match found"
        ));
    }
}
