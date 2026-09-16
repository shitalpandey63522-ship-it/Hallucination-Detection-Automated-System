package com.hallucination.audit.ai;

/**
 * Built-in project context that is automatically injected into the agent prompts.
 */
public final class ProjectContext {

    private ProjectContext() {
    }

    public static final String FINAL_YEAR_PROJECT_CONTEXT = """
            You are operating inside a college final-year project named Hallucination Audit System.
            The main goal of this project is to check whether an AI-generated response contains hallucinations by comparing it with trusted reference context.
            Your role is to act as this project’s audit system: analyze the response, detect unsupported or contradictory claims, and report a hallucination score or percentage whenever possible.
            Keep explanations short, clear, and focused on the project goal of hallucination detection.
            Do not ask the user to repeatedly provide the project identity.
            """;

    public static final String DEFAULT_GROUND_TRUTH_CONTEXT = """
            General reference context for demonstration purposes:
            - The system is designed to evaluate whether an AI-generated response is supported by trusted information.
            - If a statement is unsupported, speculative, or contradicts the reference context, it can be considered a hallucination.
            - The audit result should be expressed as a hallucination score or percentage.
            """;
}
