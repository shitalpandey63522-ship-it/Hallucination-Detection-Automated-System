package com.hallucination.audit.controller;

import com.hallucination.audit.dto.WikipediaSearchResponse;
import com.hallucination.audit.service.WikiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wiki")
public class WikiController {

    private final WikiService wikiService;

    public WikiController(WikiService wikiService) {
        this.wikiService = wikiService;
    }

    @GetMapping("/search")
    public WikipediaSearchResponse search(@RequestParam String query) {
        return wikiService.search(query);
    }
}
