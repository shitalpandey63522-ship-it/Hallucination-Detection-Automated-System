package com.hallucination.audit.service;

import com.hallucination.audit.dto.WikipediaSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

@Service
public class WikiService {

    private final RestTemplate restTemplate;
    private final WebSearchService webSearchService;
    private final Cache<String, WikipediaSearchResponse> cache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();

    public WikiService() {
        this("", 0, new WebSearchService());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WikiService(WebSearchService webSearchService) {
        this("", 0, webSearchService);
    }

    public WikiService(
            @org.springframework.beans.factory.annotation.Value("${audit.proxy.host:}") String proxyHost,
            @org.springframework.beans.factory.annotation.Value("${audit.proxy.port:0}") int proxyPort
    ) {
        this(proxyHost, proxyPort, new WebSearchService());
    }

    public WikiService(
            String proxyHost,
            int proxyPort,
            WebSearchService webSearchService
    ) {
        this.webSearchService = webSearchService != null ? webSearchService : new WebSearchService();
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

    private final java.util.concurrent.ExecutorService searchExecutor = java.util.concurrent.Executors.newCachedThreadPool();

    public static boolean isDisambiguationStub(String summary) {
        if (summary == null || summary.isBlank()) return false;
        String lower = summary.toLowerCase();
        return lower.contains("may refer to:")
                || lower.contains("most commonly refers to:")
                || lower.contains("refer to:")
                || lower.contains("can refer to:")
                || lower.contains("disambiguation page");
    }

    public WikipediaSearchResponse search(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }

        String cacheKey = query.trim().toLowerCase();
        WikipediaSearchResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        java.util.concurrent.CompletableFuture<WikipediaSearchResponse> wikiFuture = 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> fetchWikipedia(query), searchExecutor);
        java.util.concurrent.CompletableFuture<WikipediaSearchResponse> webFuture = 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> webSearchService.search(query), searchExecutor);

        try {
            java.util.concurrent.CompletableFuture.allOf(wikiFuture, webFuture)
                    .orTimeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> null)
                    .join();
        } catch (Exception ignored) {}

        WikipediaSearchResponse wikiResp = null;
        try { wikiResp = wikiFuture.getNow(null); } catch (Exception ignored) {}

        WikipediaSearchResponse webResp = null;
        try { webResp = webFuture.getNow(null); } catch (Exception ignored) {}

        boolean wikiValid = wikiResp != null && wikiResp.summary() != null 
                && wikiResp.summary().length() > 30 
                && !wikiResp.summary().contains("unavailable")
                && !isDisambiguationStub(wikiResp.summary());
        boolean webValid = webResp != null && webResp.summary() != null 
                && webResp.summary().length() > 30 
                && !webResp.summary().contains("unavailable")
                && !isDisambiguationStub(webResp.summary());

        WikipediaSearchResponse finalResult;

        if (wikiValid && webValid && wikiResp != null && webResp != null) {
            String combinedTitle = wikiResp.title() + " & Live Web Search";
            String combinedUrl = wikiResp.url() != null ? wikiResp.url() : webResp.url();
            String combinedSummary = wikiResp.summary() + "\n\n" + webResp.summary();
            finalResult = new WikipediaSearchResponse(combinedTitle, combinedUrl, combinedSummary);
        } else if (wikiValid && wikiResp != null) {
            finalResult = wikiResp;
        } else if (webValid && webResp != null) {
            finalResult = webResp;
        } else {
            finalResult = new WikipediaSearchResponse(query, null, "Reference summary unavailable right now.");
        }

        cache.put(cacheKey, finalResult);
        return finalResult;
    }

    public WikipediaSearchResponse searchMultipleParallel(java.util.List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return new WikipediaSearchResponse("Multi-Query Search", null, "No sub-queries provided.");
        }
        java.util.List<java.util.concurrent.CompletableFuture<WikipediaSearchResponse>> futures = queries.stream()
                .filter(q -> q != null && !q.isBlank())
                .map(q -> java.util.concurrent.CompletableFuture.supplyAsync(() -> search(q), searchExecutor))
                .toList();

        if (futures.isEmpty()) {
            return new WikipediaSearchResponse("Multi-Query Search", null, "No valid sub-queries provided.");
        }

        try {
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .orTimeout(4000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> null)
                    .join();
        } catch (Exception ignored) {}

        StringBuilder mergedSummary = new StringBuilder();
        String mainTitle = null;
        String mainUrl = null;
        java.util.Set<String> seenTitles = new java.util.HashSet<>();

        for (var future : futures) {
            WikipediaSearchResponse resp = null;
            try { resp = future.getNow(null); } catch (Exception ignored) {}
            if (resp != null && resp.summary() != null && !resp.summary().isBlank() 
                    && !resp.summary().contains("unavailable")
                    && !isDisambiguationStub(resp.summary())) {
                String cleanTitle = (resp.title() != null) ? resp.title().trim().toLowerCase() : "";
                if (!cleanTitle.isBlank() && seenTitles.contains(cleanTitle)) {
                    continue; // Skip duplicate topic extracts in parallel search
                }
                if (!cleanTitle.isBlank()) {
                    seenTitles.add(cleanTitle);
                }
                if (mainTitle == null) {
                    mainTitle = resp.title();
                    mainUrl = resp.url();
                }
                mergedSummary.append("=== Reference Facts: ").append(resp.title()).append(" ===\n")
                        .append(resp.summary()).append("\n\n");
            }
        }

        String finalSummary = mergedSummary.toString().trim();
        if (finalSummary.isBlank()) {
            return search(queries.get(0));
        }

        return new WikipediaSearchResponse(
                mainTitle != null ? mainTitle + " (+ Recursive Sub-Queries)" : "Multi-Query Parallel Search",
                mainUrl,
                finalSummary
        );
    }

    private WikipediaSearchResponse fetchWikipedia(String query) {
        try {
            // First try to find the best-matching page titles using the search API (up to 3)
            java.net.URI searchUrl = UriComponentsBuilder
                    .fromHttpUrl("https://en.wikipedia.org/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("format", "json")
                    .queryParam("list", "search")
                    .queryParam("srsearch", query)
                    .queryParam("srlimit", "3")
                    .build()
                    .toUri();

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
            java.net.URI url = UriComponentsBuilder
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
                    .toUri();

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

            return new WikipediaSearchResponse(firstTitle, firstUrl, finalSummary);
        } catch (Exception exception) {
            // Failover: Try the Wikimedia REST API
            try {
                String restQuery = java.util.Arrays.stream(query.trim().split("\\s+"))
                        .map(w -> w.isEmpty() ? "" : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                        .collect(java.util.stream.Collectors.joining("_"));
                String restUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + java.net.URLEncoder.encode(restQuery, "UTF-8");
                @SuppressWarnings("unchecked")
                Map<String, Object> restResponse = (Map<String, Object>) restTemplate.getForObject(restUrl, Map.class);
                if (restResponse != null && restResponse.get("extract") instanceof String extract && extract.length() > 30) {
                    String title = restResponse.get("title") instanceof String t ? t : query;
                    String urlValue = null;
                    if (restResponse.get("content_urls") instanceof Map<?, ?> urls) {
                        if (urls.get("desktop") instanceof Map<?, ?> desktop) {
                            urlValue = (String) desktop.get("page");
                        }
                    }
                    return new WikipediaSearchResponse(title, urlValue, extract);
                }
            } catch (Exception ignored) {}

            return new WikipediaSearchResponse(query, null, "Wikipedia summary unavailable right now.");
        }
    }
}
