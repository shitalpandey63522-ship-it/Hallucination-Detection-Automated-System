package com.hallucination.audit.controller;

import com.hallucination.audit.service.LocalVectorRagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.hallucination.audit.dto.RagResponse;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final LocalVectorRagService localVectorRagService;

    public RagController(LocalVectorRagService localVectorRagService) {
        this.localVectorRagService = localVectorRagService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingestKnowledge(
            @RequestParam(defaultValue = "General") String topic,
            @RequestParam(defaultValue = "user-doc") String docId,
            @RequestBody String text
    ) {
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(new RagResponse.ErrorResponse("error", "Text must not be empty"));
        }
        localVectorRagService.ingestDocument(topic, docId, text);
        return ResponseEntity.ok(new RagResponse.IngestResponse(
                "success", 
                "Document ingested into topic [" + topic + "] vector store successfully", 
                topic, 
                docId
        ));
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadKnowledgeFile(
            @RequestParam(defaultValue = "General") String topic,
            @RequestParam(required = false) String docId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new RagResponse.ErrorResponse("error", "Uploaded file must not be empty"));
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String effectiveDocId = (docId != null && !docId.isBlank()) 
                    ? docId 
                    : (originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9-]", "-") : "file-doc");

            String extractedText = com.hallucination.audit.util.FileTextExtractor.extractText(file);

            if (extractedText == null || extractedText.isBlank() || extractedText.equalsIgnoreCase("Extracted document context from PDF file.")) {
                return ResponseEntity.badRequest().body(new RagResponse.ErrorResponse(
                        "error", 
                        "Could not extract readable text from PDF. The document may be a scanned image-only PDF (without embedded text), password-protected, or corrupted. Please run OCR or upload a text-selectable PDF/DOCX file."
                ));
            }

            String safeFilename = (originalFilename != null && !originalFilename.isBlank()) ? originalFilename : "uploaded-file";
            localVectorRagService.ingestDocument(topic, effectiveDocId, extractedText);
            return ResponseEntity.ok(new RagResponse.UploadResponse(
                    "success",
                    "File [" + safeFilename + "] (" + (extractedText.length() / 1024) + " KB text extracted) ingested into topic [" + topic + "] vector database successfully!",
                    topic,
                    effectiveDocId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new RagResponse.ErrorResponse("error", "File processing failed: " + e.getMessage()));
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
    public ResponseEntity<RagResponse.ClearResponse> clearVectorDatabase(
            @RequestParam(required = false) String topic
    ) {
        localVectorRagService.clearTopicStore(topic);
        String target = (topic == null || topic.isBlank() || topic.equalsIgnoreCase("all")) ? "All Topics" : topic;
        return ResponseEntity.ok(new RagResponse.ClearResponse(
                "success",
                "Vector database for [" + target + "] cleared successfully!"
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<RagResponse.SearchResponse> searchKnowledge(
            @RequestParam(required = false) String topic,
            @RequestParam String query
    ) {
        String match = localVectorRagService.findRelevantContext(topic, query);
        return ResponseEntity.ok(new RagResponse.SearchResponse(
                topic != null ? topic : "All Topics",
                query,
                match != null ? match : "No vector similarity match found"
        ));
    }
}
