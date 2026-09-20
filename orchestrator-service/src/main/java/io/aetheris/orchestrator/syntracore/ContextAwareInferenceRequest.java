package io.aetheris.orchestrator.syntracore;

import java.util.Objects;
import java.util.Set;

public record ContextAwareInferenceRequest(
        ContextAssemblyRequest contextRequest,
        String userInput,
        Set<ModelCapability> requiredCapabilities,
        HardwareCapacity hardware,
        int requiredContextTokens,
        int maxOutputTokens,
        boolean allowCpuFallback) {

    public ContextAwareInferenceRequest {
        contextRequest = Objects.requireNonNull(contextRequest, "contextRequest");
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("userInput must not be blank");
        }
        userInput = userInput.trim();
        requiredCapabilities = Set.copyOf(Objects.requireNonNull(requiredCapabilities, "requiredCapabilities"));
        if (requiredCapabilities.isEmpty()) {
            throw new IllegalArgumentException("requiredCapabilities must not be empty");
        }
        hardware = Objects.requireNonNull(hardware, "hardware");
        if (requiredContextTokens <= 0) {
            throw new IllegalArgumentException("requiredContextTokens must be positive");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }
}
