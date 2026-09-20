package io.aetheris.orchestrator.syntracore;

public record ContextAwareInferenceEvidence(
        int contextEvidenceCount,
        int citationCount,
        int emittedChunkCount,
        boolean terminalObserved,
        boolean cancelled,
        boolean sequenceContinuous) {

    public ContextAwareInferenceEvidence {
        if (contextEvidenceCount < 0) {
            throw new IllegalArgumentException("contextEvidenceCount must not be negative");
        }
        if (citationCount < 0) {
            throw new IllegalArgumentException("citationCount must not be negative");
        }
        if (emittedChunkCount < 0) {
            throw new IllegalArgumentException("emittedChunkCount must not be negative");
        }
    }
}
