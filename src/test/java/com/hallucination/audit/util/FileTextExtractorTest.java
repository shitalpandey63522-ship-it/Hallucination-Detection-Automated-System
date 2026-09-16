package com.hallucination.audit.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class FileTextExtractorTest {

    @Test
    void shouldExtractPlainTextCleanly() throws Exception {
        String content = "Hello world! This is a test file for RAG embedding.";
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content.getBytes());

        String extracted = FileTextExtractor.extractText(file);
        assertEquals(content, extracted);
    }

    @Test
    void shouldSanitizeBinaryControlCharactersAndQuestionMarks() throws Exception {
        byte[] rawBytes = "Sample\u0000Text\u0001With\u0002Binary\u0003Control\u0004Chars???".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", rawBytes);

        String extracted = FileTextExtractor.extractText(file);
        assertFalse(extracted.contains("???"));
        assertTrue(extracted.contains("Sample Text With Binary Control Chars"));
    }
}
