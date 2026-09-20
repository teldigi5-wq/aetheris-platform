package io.aetheris.orchestrator.syntracore;

public record ModelInvocation(
        String modelId,
        String input,
        int maxOutputTokens) {

    public ModelInvocation {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }
}
