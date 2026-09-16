package com.hallucination.audit.service;

import com.hallucination.audit.dto.WikipediaSearchResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebSearchService {

    private final RestTemplate restTemplate;
    private final Map<String, WikipediaSearchResponse> cache = new ConcurrentHashMap<>();

    public WebSearchService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2500);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    public WikipediaSearchResponse search(String query) {
        if (query == null || query.isBlank()) {
            return new WikipediaSearchResponse(query, null, "No query provided for web search.");
        }

        String cacheKey = query.trim().toLowerCase();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://html.duckduckgo.com/html/")
                    .queryParam("q", query)
                    .build()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            headers.set("Accept-Language", "en-US,en;q=0.5");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            HttpMethod getMethod = HttpMethod.GET;
            ResponseEntity<String> response = restTemplate.exchange(uri, getMethod, entity, String.class);

            String html = response.getBody();
            if (html == null || html.isBlank()) {
                return new WikipediaSearchResponse(query, null, "Web search returned empty body.");
            }

            List<String> snippets = extractSnippetsFromHtml(html);
            if (snippets.isEmpty()) {
                return new WikipediaSearchResponse(query, null, "No relevant web search snippets found.");
            }

            String aggregatedSummary = "🌐 Live Web Search Results:\n\n" + String.join("\n\n", snippets);
            WikipediaSearchResponse searchResponse = new WikipediaSearchResponse(
                    "Web Search: " + query,
                    "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(query, "UTF-8"),
                    aggregatedSummary
            );

            cache.put(cacheKey, searchResponse);
            return searchResponse;
        } catch (Exception e) {
            return new WikipediaSearchResponse(query, null, "Web search unavailable right now.");
        }
    }

    private List<String> extractSnippetsFromHtml(String html) {
        List<String> snippets = new ArrayList<>();
        Pattern snippetPattern = Pattern.compile("(?s)<a\\s+class=\"result__snippet\"[^>]*>(.*?)</a>");
        Matcher matcher = snippetPattern.matcher(html);

        while (matcher.find() && snippets.size() < 4) {
            String rawSnippet = matcher.group(1);
            String cleaned = cleanHtmlText(rawSnippet);
            if (!cleaned.isBlank() && cleaned.length() > 20) {
                snippets.add("• " + cleaned);
            }
        }
        return snippets;
    }

    private String cleanHtmlText(String htmlSnippet) {
        if (htmlSnippet == null) return "";
        String text = htmlSnippet.replaceAll("(?i)<[^>]+>", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#x27;", "'")
                .replaceAll("&#x2F;", "/")
                .replaceAll("\\s+", " ")
                .trim();
        return text;
    }
}
