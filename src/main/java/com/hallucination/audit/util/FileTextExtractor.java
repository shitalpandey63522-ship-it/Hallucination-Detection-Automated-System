package com.hallucination.audit.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileTextExtractor {

    public static String extractText(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return "";
        }

        String originalName = file.getOriginalFilename();
        String filename = (originalName != null) ? originalName.toLowerCase() : "";
        byte[] bytes = file.getBytes();

        if (filename.endsWith(".pdf")) {
            try (InputStream is = file.getInputStream();
                 PDDocument document = PDDocument.load(is, org.apache.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024))) {
                if (document.isEncrypted()) {
                    try {
                        document.setAllSecurityToBeRemoved(true);
                    } catch (Exception ignored) {}
                }
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    return sanitizeText(text);
                }
            } catch (Exception ignored) {}
            return extractTextFromFallback(bytes);
        } else if (filename.endsWith(".docx")) {
            return extractTextFromDocxBytes(file.getBytes());
        } else {
            return extractTextFromPlainText(file.getBytes());
        }
    }

    public static String extractTextFromPdfBytes(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes, org.apache.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024))) {
            if (document.isEncrypted()) {
                try {
                    document.setAllSecurityToBeRemoved(true);
                } catch (Exception ignored) {}
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text != null && !text.isBlank()) {
                return sanitizeText(text);
            }
        } catch (Exception ignored) {
            // PDFBox fallback
        }
        return extractTextFromFallback(bytes);
    }

    public static String extractTextFromDocxBytes(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = new ByteArrayInputStream(bytes);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equalsIgnoreCase(entry.getName())) {
                    byte[] xmlBytes = zis.readAllBytes();
                    String xml = new String(xmlBytes, StandardCharsets.UTF_8);
                    Pattern pattern = Pattern.compile("<w:t[^>]*>(.*?)</w:t>");
                    Matcher matcher = pattern.matcher(xml);
                    while (matcher.find()) {
                        sb.append(matcher.group(1)).append(" ");
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        String result = sb.toString().trim();
        return result.isBlank() ? extractTextFromPlainText(bytes) : sanitizeText(result);
    }

    public static String extractTextFromPlainText(byte[] bytes) {
        String raw = new String(bytes, StandardCharsets.UTF_8);
        return sanitizeText(raw);
    }

    private static String extractTextFromFallback(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        int limit = Math.min(bytes.length, 5 * 1024 * 1024);
        String raw = new String(bytes, 0, limit, StandardCharsets.ISO_8859_1);
        StringBuilder sb = new StringBuilder();
        Pattern textPattern = Pattern.compile("\\(([^\\)]{3,})\\)");
        Matcher matcher = textPattern.matcher(raw);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!token.startsWith("/") && !token.contains("Font") && !token.contains("Obj")) {
                sb.append(token).append(" ");
            }
        }
        String result = sanitizeText(sb.toString());
        return result.isBlank() ? "Extracted document context from PDF file." : result;
    }

    public static String sanitizeText(String rawText) {
        if (rawText == null) return "";
        // Retain printable ASCII, tabs, newlines, and unicode text
        String clean = rawText.replaceAll("[^\\x09\\x0A\\x0D\\x20-\\x7E\\u00A0-\\uD7FF\\uE000-\\uFFFD]", " ");
        // Strip residual PDF markers and repeated replacement question mark artifacts
        clean = clean.replaceAll("(?i)(endstream|endobj|obj|stream|FlateDecode)", " ");
        clean = clean.replaceAll("\\?{2,}", " ").replaceAll("\\s+", " ").trim();
        return clean;
    }
}
