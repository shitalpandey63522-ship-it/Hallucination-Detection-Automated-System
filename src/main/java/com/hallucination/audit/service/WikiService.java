package com.hallucination.audit.service;

import com.hallucination.audit.dto.WikipediaSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
public class WikiService {

    private final RestTemplate restTemplate;
    private final java.util.Map<String, WikipediaSearchResponse> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public WikiService() {
        this("", 0);
    }

    public WikiService(
            @org.springframework.beans.factory.annotation.Value("${audit.proxy.host:}") String proxyHost,
            @org.springframework.beans.factory.annotation.Value("${audit.proxy.port:0}") int proxyPort
    ) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500); // 1.5s connection timeout for fast response
        factory.setReadTimeout(2000);    // 2s read timeout
        
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            java.net.Proxy proxy = new java.net.Proxy(
                java.net.Proxy.Type.HTTP, 
                new java.net.InetSocketAddress(proxyHost, proxyPort)
            );
            factory.setProxy(proxy);
        } else {
            System.setProperty("java.net.useSystemProxies", "true");
        }
        this.restTemplate = new RestTemplate(factory);
        
        // Add User-Agent & Connection interceptor to comply with Wikipedia API policy and prevent socket hangups
        this.restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("User-Agent", "AutomatedRealtimeHallucinationDetector/1.0 (contact: 23se02cs062@ppsu.ac.in)");
            request.getHeaders().set("Accept", "application/json");
            request.getHeaders().set("Connection", "close");
            return execution.execute(request, body);
        });
    }

    public WikipediaSearchResponse search(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }

        String cacheKey = query.trim().toLowerCase();
        
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        try {
            // First try to find the best-matching page titles using the search API (up to 3)
            String searchUrl = UriComponentsBuilder
                    .fromHttpUrl("https://en.wikipedia.org/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("format", "json")
                    .queryParam("list", "search")
                    .queryParam("srsearch", query)
                    .queryParam("srlimit", "3")
                    .build()
                    .encode()
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> searchResponse = (Map<String, Object>) restTemplate.getForObject(searchUrl, Map.class);
            java.util.List<String> titles = new java.util.ArrayList<>();
            if (searchResponse != null && searchResponse.get("query") instanceof Map<?, ?> searchQuery) {
                Object searchList = searchQuery.get("search");
                if (searchList instanceof java.util.List<?> list && !list.isEmpty()) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> searchItem) {
                            Object titleObj = searchItem.get("title");
                            if (titleObj instanceof String t) {
                                titles.add(t);
                            }
                        }
                    }
                }
            }

            if (titles.isEmpty()) {
                titles.add(query);
            }

            String titlesParam = String.join("|", titles);

            // Now fetch the page extracts for the selected titles
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://en.wikipedia.org/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("format", "json")
                    .queryParam("prop", "extracts|info")
                    .queryParam("exintro", "1")
                    .queryParam("explaintext", "1")
                    .queryParam("inprop", "url")
                    .queryParam("redirects", "1")
                    .queryParam("titles", titlesParam)
                    .build()
                    .encode()
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) restTemplate.getForObject(url, Map.class);
            if (response == null || response.get("query") == null) {
                return new WikipediaSearchResponse(titles.get(0), null, "No summary available");
            }

            Object queryObject = response.get("query");
            if (!(queryObject instanceof Map<?, ?> queryMap)) {
                return new WikipediaSearchResponse(titles.get(0), null, "No summary available");
            }

            Object pagesObject = queryMap.get("pages");
            if (!(pagesObject instanceof Map<?, ?> pages) || pages.isEmpty()) {
                return new WikipediaSearchResponse(titles.get(0), null, "No summary available");
            }

            StringBuilder aggregatedSummary = new StringBuilder();
            String firstTitle = null;
            String firstUrl = null;

            for (Map.Entry<?, ?> pageEntry : pages.entrySet()) {
                Object pageObject = pageEntry.getValue();
                if (pageObject instanceof Map<?, ?> page) {
                    String title = page.get("title") instanceof String titleValue ? titleValue : "";
                    String summary = page.get("extract") instanceof String extract ? extract : "";
                    if (firstTitle == null) {
                        firstTitle = title;
                        firstUrl = page.get("fullurl") instanceof String fullUrl ? fullUrl : null;
                    }
                    if (!summary.isBlank()) {
                        aggregatedSummary.append("--- ").append(title).append(" ---\n").append(summary).append("\n\n");
                    }
                }
            }
            
            if (firstTitle == null) {
                firstTitle = titles.get(0);
            }

            String finalSummary = aggregatedSummary.toString().trim();
            if (finalSummary.isBlank()) {
                finalSummary = "No summary available";
            }

            WikipediaSearchResponse responseObj = new WikipediaSearchResponse(firstTitle, firstUrl, finalSummary);
            cache.put(cacheKey, responseObj);
            return responseObj;
        } catch (Exception exception) {
            // Failover: Try the Wikimedia REST API (often bypasses firewall blocks on main search API)
            try {
                String restQuery = java.util.Arrays.stream(query.trim().split("\\s+"))
                        .map(w -> w.isEmpty() ? "" : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                        .collect(java.util.stream.Collectors.joining("_"));
                String restUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + java.net.URLEncoder.encode(restQuery, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> restResponse = (Map<String, Object>) restTemplate.getForObject(restUrl, Map.class);
                if (restResponse != null && restResponse.get("extract") instanceof String extract) {
                    String title = restResponse.get("title") instanceof String t ? t : query;
                    String urlValue = null;
                    if (restResponse.get("content_urls") instanceof Map<?, ?> urls) {
                        if (urls.get("desktop") instanceof Map<?, ?> desktop) {
                            urlValue = (String) desktop.get("page");
                        }
                    }
                    WikipediaSearchResponse responseObj = new WikipediaSearchResponse(title, urlValue, extract);
                    cache.put(cacheKey, responseObj);
                    return responseObj;
                }
            } catch (Exception failoverException) {
                // Ignore, proceed to return default error response
            }
            return new WikipediaSearchResponse(query, null, "Wikipedia summary unavailable right now.");
        }
    }
}
