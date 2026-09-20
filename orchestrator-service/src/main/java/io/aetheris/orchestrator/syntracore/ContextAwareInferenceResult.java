package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.Objects;

public record ContextAwareInferenceResult(
        ModelRouteSelection route,
        ContextPack context,
        List<String> citations,
        List<ModelStreamChunk> chunks,
        String output,
        ContextAwareInferenceEvidence executionEvidence) {

    public ContextAwareInferenceResult {
        route = Objects.requireNonNull(route, "route");
        context = Objects.requireNonNull(context, "context");
        citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        output = Objects.requireNonNull(output, "output");
        executionEvidence = Objects.requireNonNull(executionEvidence, "executionEvidence");

        if (!citations.equals(context.citations())) {
            throw new IllegalArgumentException("result citations must exactly match assembled context citations");
        }
        String renderedOutput = chunks.stream().map(ModelStreamChunk::text).reduce("", String::concat);
        if (!renderedOutput.equals(output)) {
            throw new IllegalArgumentException("output must exactly match emitted stream text");
        }
        if (executionEvidence.contextEvidenceCount() != context.evidence().size()) {
            throw new IllegalArgumentException("execution evidence context count must match context pack");
        }
        if (executionEvidence.citationCount() != citations.size()) {
            throw new IllegalArgumentException("execution evidence citation count must match result citations");
        }
        if (executionEvidence.emittedChunkCount() != chunks.size()) {
            throw new IllegalArgumentException("execution evidence chunk count must match emitted chunks");
        }
    }
}
