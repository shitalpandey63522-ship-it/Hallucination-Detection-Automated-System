package com.hallucination.audit.service;

import com.hallucination.audit.dto.ExtractedCitation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalNlpUtils {

    public static List<String> tokenizeAndClean(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] tokens = text.split("\\s+");
        List<String> cleanedTokens = new ArrayList<>();
        for (String t : tokens) {
            String clean = t.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (!clean.isBlank() && !isStopWord(clean)) {
                cleanedTokens.add(clean);
            }
        }
        return cleanedTokens;
    }

    public static boolean isStopWord(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String[] stopWords = {
                "a", "an", "and", "are", "as", "at", "be", "been", "being", "by", "for", "from",
                "had", "has", "have", "he", "her", "his", "i", "if", "in", "into", "is", "it", "its",
                "me", "my", "no", "not", "of", "on", "or", "our", "she", "that", "the", "their", "them",
                "they", "this", "to", "was", "we", "were", "what", "when", "where", "which", "who",
                "why", "will", "with", "you", "your"
        };
        for (String stopWord : stopWords) {
            if (stopWord.equals(token)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> splitSentencesLocally(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // Protect numeric decimals (e.g. 1.5, 3.14) and common abbreviations (e.g. Dr., Mr., Mrs., Prof., e.g., i.e., vs., U.S.)
        String protectedText = text.replaceAll("(?<=\\b\\d)\\.(?=\\d)", "___DOT___")
                .replaceAll("(?i)\\b(dr|mr|mrs|ms|prof|sr|jr|vs|st|e\\.g|i\\.e|etc|u\\.s|no|vol|fig|pp)\\.", "$1___DOT___");

        String[] split = protectedText.split("(?<=[.!?])\\s+(?=[A-Z\"'\\d])|(?<=[.!?])\\s+$");
        List<String> result = new ArrayList<>();
        for (String s : split) {
            String restored = s.replaceAll("___DOT___", ".").trim();
            if (!restored.isBlank()) {
                result.add(restored);
            }
        }
        return result.isEmpty() ? List.of(text.trim()) : result;
    }

    public static List<ExtractedCitation> extractCitationsLocally(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ExtractedCitation> result = new ArrayList<>();
        
        Pattern pattern = Pattern.compile("\\(([A-Za-z\\s&.,]+,\\s*\\d{4}(?:,\\s*[^)]+)?)\\)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String marker = matcher.group(0);
            String detail = matcher.group(1);
            result.add(new ExtractedCitation(marker, detail, "HALLUCINATED", "Unverified academic publication reference detected in ungrounded text.", null));
        }
        
        Pattern pattern2 = Pattern.compile("([A-Z][a-zA-Z]+(?:\\s+et\\s+al\\.)?)\\s*\\((\\d{4})(?:,\\s*([^)]+))?\\)");
        Matcher matcher2 = pattern2.matcher(text);
        while (matcher2.find()) {
            String marker = matcher2.group(0);
            String author = matcher2.group(1);
            String year = matcher2.group(2);
            String extra = matcher2.group(3);
            String detail = author + " (" + year + ")" + (extra != null ? ", " + extra : "");
            result.add(new ExtractedCitation(marker, detail, "HALLUCINATED", "Unverified or fabricated academic citation reference.", null));
        }
        return result;
    }

    public static String extractTopicLocally(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.replaceAll("(?i)^(what is|what are|who is|who was|tell me about|explain|describe|how does|how do|can you tell me|certainly|surely|here is|here are|the case is|the topic is|yes|ok|okay)[!\\s,.:?]*", "").trim();
        cleaned = cleaned.replaceAll("[?!=.]+$", "").trim();
        String cleanedNoTitles = cleaned.replaceAll("(?i)\\b(badshah|samrat|emperor|king|queen|president|prime minister|lord|sir|dr|doctor|prof|professor|saint|ji|shri|raja|sultan|pasha|khan|the great)\\b", "").replaceAll("\\s+", " ").trim();
        if (!cleanedNoTitles.isBlank()) {
            cleaned = cleanedNoTitles;
        }
        if (cleaned.isBlank()) {
            cleaned = text.trim();
        }
        
        Pattern casePattern = Pattern.compile("([A-Z][a-zA-Z0-9\\s]+)\\sv\\.?\\s([A-Z][a-zA-Z0-9\\s]+)");
        Matcher caseMatcher = casePattern.matcher(cleaned);
        if (caseMatcher.find()) {
            return (caseMatcher.group(1).trim() + " v. " + caseMatcher.group(2).trim()).replaceAll("\\s+", " ");
        }

        String[] words = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String word : words) {
            String cleanWord = word.replaceAll("[^a-zA-Z0-9]", "");
            if (!cleanWord.isBlank() && !isStopWord(cleanWord.toLowerCase())) {
                sb.append(word).append(" ");
                count++;
                if (count >= 6) break;
            }
        }
        String res = sb.toString().trim();
        if (res.isBlank() || isPronounOrGeneric(res)) {
            return cleaned.isBlank() ? text.trim() : cleaned;
        }
        return res;
    }

    private static boolean isPronounOrGeneric(String topic) {
        if (topic == null || topic.isBlank()) return true;
        String t = topic.trim().toLowerCase();
        return t.equals("he") || t.equals("she") || t.equals("it") || t.equals("they") 
            || t.equals("this") || t.equals("that") || t.equals("these") || t.equals("those")
            || t.equals("there") || t.equals("here") || t.equals("what") || t.equals("who")
            || t.equals("how") || t.equals("why") || t.equals("when") || t.equals("where");
    }
}
