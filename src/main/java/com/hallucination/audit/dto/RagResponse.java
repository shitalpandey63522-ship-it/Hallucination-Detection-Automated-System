package com.hallucination.audit.dto;

public class RagResponse {

    public record IngestResponse(String status, String message, String topic, String docId) {}
    
    public record UploadResponse(String status, String message, String topic, String docId) {}
    
    public record ClearResponse(String status, String message) {}
    
    public record SearchResponse(String topic, String query, String matchedContext) {}
    
    public record ErrorResponse(String status, String message) {}
}
