package com.hallucination.audit.controller;

import com.hallucination.audit.dto.WikipediaSearchResponse;
import com.hallucination.audit.service.WikiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WikiController.class)
class WikiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WikiService wikiService;

    @Test
    void shouldReturnWikipediaSummaryForQuery() throws Exception {
        when(wikiService.search("Paris")).thenReturn(new WikipediaSearchResponse(
                "Paris",
                "https://en.wikipedia.org/wiki/Paris",
                "Paris is the capital and most populous city of France."
        ));

        mockMvc.perform(get("/api/wiki/search").param("query", "Paris"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Paris"))
                .andExpect(jsonPath("$.summary").value("Paris is the capital and most populous city of France."));
    }
}
