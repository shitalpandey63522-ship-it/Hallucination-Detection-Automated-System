package com.hallucination.audit.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

import static com.hallucination.audit.ai.ProjectContext.FINAL_YEAR_PROJECT_CONTEXT;

/**
 * Generates responses to user queries using an LLM agent.
 * This is the "generation" stage of the pipeline.
 */
@AiService
public interface AgentResponseGenerator {

    @SystemMessage("""
            You are a helpful AI assistant that provides accurate, informative responses.
            When answering questions, be factual and cite relevant information from the context if available.
            Keep responses concise and clear.
            
            """ + FINAL_YEAR_PROJECT_CONTEXT + """
            """)
    @UserMessage("""
            Context (if available):
            {{context}}

            Question:
            {{question}}
            """)
    String generateResponse(@V("question") String question, @V("context") String context);
}
