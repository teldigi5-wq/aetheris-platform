package io.aetheris.orchestrator.syntracore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SyntraContextAwareInferenceOrchestrator {
    private final SyntraContextAssemblyService contextAssembly;
    private final SyntraModelRouter router;
    private final Map<String, SyntraModelRuntime> runtimes;

    public SyntraContextAwareInferenceOrchestrator(
            SyntraContextAssemblyService contextAssembly,
            List<SyntraModelRuntime> runtimes) {
        this(contextAssembly, new SyntraModelRouter(), runtimes);
    }

    SyntraContextAwareInferenceOrchestrator(
            SyntraContextAssemblyService contextAssembly,
            SyntraModelRouter router,
            List<SyntraModelRuntime> runtimes) {
        this.contextAssembly = Objects.requireNonNull(contextAssembly, "contextAssembly");
        this.router = Objects.requireNonNull(router, "router");
        Objects.requireNonNull(runtimes, "runtimes");

        Map<String, SyntraModelRuntime> indexed = new LinkedHashMap<>();
        for (SyntraModelRuntime runtime : runtimes) {
            Objects.requireNonNull(runtime, "runtime");
            if (runtime.providerId() == null || runtime.providerId().isBlank()) {
                throw new IllegalArgumentException("runtime providerId must not be blank");
            }
            SyntraModelRuntime previous = indexed.putIfAbsent(runtime.providerId(), runtime);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate runtime providerId: " + runtime.providerId());
            }
        }
        this.runtimes = Map.copyOf(indexed);
    }

    public ContextAwareInferenceResult stream(
            ContextAwareInferenceRequest request,
            Consumer<ModelStreamChunk> sink,
            BooleanSupplier cancellationRequested) {
        return stream(request, sink, cancellationRequested, evidence -> { });
    }

    public ContextAwareInferenceResult stream(
            ContextAwareInferenceRequest request,
            Consumer<ModelStreamChunk> sink,
            BooleanSupplier cancellationRequested,
            Consumer<ContextAwareInferenceEvidence> evaluationSink) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Objects.requireNonNull(evaluationSink, "evaluationSink");

        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException("inference cancelled before context assembly");
        }

        ContextPack context = contextAssembly.assemble(request.contextRequest());
        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException("inference cancelled before model routing");
        }

        List<ModelRuntimeCandidate> candidates = localCandidates();
        int requiredContextTokens = Math.max(
                request.requiredContextTokens(),
                Math.max(1, context.estimatedContextTokens()));
        ModelRouteRequest routeRequest = new ModelRouteRequest(
                request.requiredCapabilities(),
                requiredContextTokens,
                true,
                true,
                true,
                request.allowCpuFallback(),
                true);

        ModelRouteDecision decision = router.route(routeRequest, request.hardware(), candidates);
        ModelRouteSelection selection = decision.selection()
                .orElseThrow(() -> new InferenceRoutingException(decision.rejectionReasons()));
        if (selection.executionTarget() == ExecutionTarget.REMOTE) {
            throw new IllegalStateException("context-aware local inference must never select a remote target");
        }

        SyntraModelRuntime runtime = runtimes.get(selection.providerId());
        if (runtime == null) {
            throw new IllegalStateException("selected runtime provider is not registered: " + selection.providerId());
        }
        boolean selectedModelPresent = runtime.models().stream()
                .anyMatch(candidate -> candidate.local()
                        && candidate.modelId().equals(selection.modelId())
                        && candidate.providerId().equals(selection.providerId()));
        if (!selectedModelPresent) {
            throw new IllegalStateException("selected model is not exposed by the selected runtime");
        }

        ModelInvocation invocation = context.toModelInvocation(
                selection.modelId(),
                request.userInput(),
                request.maxOutputTokens());
        if (!invocation.evidenceAddresses().equals(context.citations())) {
            throw new IllegalStateException("model invocation lost assembled context citations");
        }

        List<ModelStreamChunk> chunks = new ArrayList<>();
        AtomicLong nextSequence = new AtomicLong(0);
        AtomicBoolean terminalObserved = new AtomicBoolean(false);
        Consumer<ModelStreamChunk> validatingSink = chunk -> {
            Objects.requireNonNull(chunk, "runtime emitted a null chunk");
            if (terminalObserved.get()) {
                throw new IllegalStateException("runtime emitted a chunk after terminal completion");
            }
            long expected = nextSequence.get();
            if (chunk.sequence() != expected) {
                throw new IllegalStateException(
                        "runtime stream sequence discontinuity: expected " + expected + " but got " + chunk.sequence());
            }
            chunks.add(chunk);
            nextSequence.incrementAndGet();
            if (chunk.terminal()) {
                terminalObserved.set(true);
            }
            sink.accept(chunk);
        };

        runtime.stream(invocation, validatingSink, cancellationRequested);
        boolean cancelled = cancellationRequested.getAsBoolean();
        if (!cancelled && !terminalObserved.get()) {
            throw new IllegalStateException("runtime ended without a terminal chunk");
        }

        ContextAwareInferenceEvidence executionEvidence = new ContextAwareInferenceEvidence(
                context.evidence().size(),
                context.citations().size(),
                chunks.size(),
                terminalObserved.get(),
                cancelled,
                true);
        evaluationSink.accept(executionEvidence);

        String output = chunks.stream().map(ModelStreamChunk::text).reduce("", String::concat);
        return new ContextAwareInferenceResult(
                selection,
                context,
                context.citations(),
                chunks,
                output,
                executionEvidence);
    }

    private List<ModelRuntimeCandidate> localCandidates() {
        List<ModelRuntimeCandidate> candidates = new ArrayList<>();
        for (SyntraModelRuntime runtime : runtimes.values()) {
            List<ModelRuntimeCandidate> runtimeModels = Objects.requireNonNull(
                    runtime.models(),
                    "runtime models must not be null");
            for (ModelRuntimeCandidate candidate : runtimeModels) {
                Objects.requireNonNull(candidate, "runtime candidate");
                if (!runtime.providerId().equals(candidate.providerId())) {
                    throw new IllegalStateException("runtime exposed a candidate for a different provider");
                }
                if (candidate.local()) {
                    candidates.add(candidate);
                }
            }
        }
        return List.copyOf(candidates);
    }
}
