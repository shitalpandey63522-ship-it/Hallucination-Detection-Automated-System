package com.hallucination.audit.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class GeminiConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model-name:gemini-3.5-flash}") String modelName,
            @Value("${gemini.temperature:0.0}") double temperature,
            @Value("${gemini.timeout-seconds:120}") int timeoutSeconds
    ) {
        System.out.println("DEBUG - Loading GeminiConfig:");
        System.out.println("  modelName: " + modelName);
        System.out.println("  temperature: " + temperature);
        System.out.println("  timeoutSeconds: " + timeoutSeconds);
        System.out.println("  apiKey length: " + (apiKey != null ? apiKey.length() : 0));
        if (apiKey != null && apiKey.length() > 8) {
            System.out.println("  apiKey mask: " + apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4));
        }
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
