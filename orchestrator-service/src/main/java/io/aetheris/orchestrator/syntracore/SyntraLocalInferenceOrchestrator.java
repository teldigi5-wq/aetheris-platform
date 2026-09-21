package io.aetheris.orchestrator.syntracore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SyntraLocalInferenceOrchestrator {
    private final SyntraModelRouter modelRouter;
    private final List<SyntraModelRuntime> runtimes;
    private final List<LocalInferenceEvaluationHook> evaluationHooks;

    public SyntraLocalInferenceOrchestrator(
            SyntraModelRouter modelRouter,
            List<SyntraModelRuntime> runtimes) {
        this(modelRouter, runtimes, List.of());
    }

    public SyntraLocalInferenceOrchestrator(
            SyntraModelRouter modelRouter,
            List<SyntraModelRuntime> runtimes,
            List<LocalInferenceEvaluationHook> evaluationHooks) {
        this.modelRouter = Objects.requireNonNull(modelRouter, "modelRouter");
        this.runtimes = List.copyOf(Objects.requireNonNull(runtimes, "runtimes"));
        this.evaluationHooks = List.copyOf(Objects.requireNonNull(evaluationHooks, "evaluationHooks"));
        for (SyntraModelRuntime runtime : this.runtimes) {
            Objects.requireNonNull(runtime, "runtime");
        }
        for (LocalInferenceEvaluationHook hook : this.evaluationHooks) {
            Objects.requireNonNull(hook, "evaluationHook");
        }
    }

    public LocalInferenceResult infer(
            ContextAwareInferenceRequest request,
            HardwareCapacity hardware,
            BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(hardware, "hardware");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");

        RuntimeCatalog catalog = buildCatalog();
        ModelRouteDecision decision = modelRouter.route(
                request.routeRequest(),
                hardware,
                catalog.candidates());

        if (!decision.selected()) {
            return evaluate(LocalInferenceResult.unavailable(
                    request.task(),
                    request.contextPack().citations(),
                    decision.rejectionReasons()));
        }

        ModelRouteSelection selection = decision.selection().orElseThrow();
        ModelRuntimeCandidate selectedCandidate = catalog.candidatesByKey().get(
                selection.providerId() + "/" + selection.modelId());
        if (selectedCandidate == null) {
            throw new IllegalStateException("router selected a model outside the runtime catalog");
        }
        if (!selectedCandidate.local() || selection.executionTarget() == ExecutionTarget.REMOTE) {
            throw new IllegalStateException("local inference orchestration cannot select a remote runtime");
        }

        SyntraModelRuntime runtime = catalog.runtimesByProvider().get(selection.providerId());
        if (runtime == null) {
            throw new IllegalStateException("router selected an unknown runtime provider");
        }

        ModelInvocation invocation = request.contextPack().toModelInvocation(
                selection.modelId(),
                request.userInput(),
                request.maxOutputTokens());
        if (!request.contextPack().citations().equals(invocation.evidenceAddresses())) {
            throw new IllegalStateException("context citations changed before model invocation");
        }

        if (cancellationRequested.getAsBoolean()) {
            return evaluate(LocalInferenceResult.cancelled(
                    request.task(),
                    selection,
                    "",
                    invocation.evidenceAddresses(),
                    decision.rejectionReasons(),
                    0));
        }

        StringBuilder output = new StringBuilder();
        long[] expectedSequence = {0L};
        int[] emittedChunks = {0};
        boolean[] terminalObserved = {false};

        Consumer<ModelStreamChunk> guardedSink = chunk -> {
            Objects.requireNonNull(chunk, "stream chunk");
            if (cancellationRequested.getAsBoolean()) {
                return;
            }
            if (terminalObserved[0]) {
                throw new IllegalStateException("runtime emitted a chunk after the terminal frame");
            }
            if (chunk.sequence() != expectedSequence[0]) {
                throw new IllegalStateException(
                        "runtime stream sequence discontinuity: expected "
                                + expectedSequence[0]
                                + " but received "
                                + chunk.sequence());
            }
            expectedSequence[0]++;
            emittedChunks[0]++;
            output.append(chunk.text());
            if (chunk.terminal()) {
                terminalObserved[0] = true;
            }
        };

        runtime.stream(invocation, guardedSink, cancellationRequested);

        if (terminalObserved[0]) {
            return evaluate(LocalInferenceResult.completed(
                    request.task(),
                    selection,
                    output.toString(),
                    invocation.evidenceAddresses(),
                    decision.rejectionReasons(),
                    emittedChunks[0]));
        }
        if (cancellationRequested.getAsBoolean()) {
            return evaluate(LocalInferenceResult.cancelled(
                    request.task(),
                    selection,
                    output.toString(),
                    invocation.evidenceAddresses(),
                    decision.rejectionReasons(),
                    emittedChunks[0]));
        }
        throw new IllegalStateException("runtime stream ended without cancellation or a terminal frame");
    }

    private RuntimeCatalog buildCatalog() {
        Map<String, SyntraModelRuntime> runtimesByProvider = new LinkedHashMap<>();
        Map<String, ModelRuntimeCandidate> candidatesByKey = new LinkedHashMap<>();
        List<ModelRuntimeCandidate> candidates = new ArrayList<>();

        for (SyntraModelRuntime runtime : runtimes) {
            String providerId = runtime.providerId();
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalStateException("runtime providerId must not be blank");
            }
            if (runtimesByProvider.putIfAbsent(providerId, runtime) != null) {
                throw new IllegalStateException("duplicate runtime providerId: " + providerId);
            }

            List<ModelRuntimeCandidate> runtimeModels = Objects.requireNonNull(
                    runtime.models(),
                    "runtime models");
            for (ModelRuntimeCandidate candidate : runtimeModels) {
                Objects.requireNonNull(candidate, "runtime candidate");
                if (!providerId.equals(candidate.providerId())) {
                    throw new IllegalStateException("runtime candidate providerId does not match its provider");
                }
                if (AdapterRuntimeRegistration.isAdapterModelId(candidate.modelId())) {
                    throw new IllegalStateException(
                            "adapter model IDs must enter the catalog through verified adapter registrations");
                }
                addCandidate(candidate, candidatesByKey, candidates);
            }

            if (runtime instanceof AdapterAwareSyntraModelRuntime adapterRuntime) {
                List<AdapterRuntimeRegistration> registrations = Objects.requireNonNull(
                        adapterRuntime.adapterRegistrations(),
                        "adapter registrations");
                for (AdapterRuntimeRegistration registration : registrations) {
                    Objects.requireNonNull(registration, "adapter registration");
                    ModelRuntimeCandidate candidate = registration.candidate();
                    if (!providerId.equals(candidate.providerId())) {
                        throw new IllegalStateException(
                                "adapter registration providerId does not match its runtime provider");
                    }
                    boolean baseModelPresent = runtimeModels.stream()
                            .filter(Objects::nonNull)
                            .anyMatch(model -> providerId.equals(model.providerId())
                                    && registration.identity().baseModelId().equals(model.modelId())
                                    && model.local());
                    if (!baseModelPresent) {
                        throw new IllegalStateException(
                                "adapter registration base model is not present on the same local runtime");
                    }

                    var currentObservation = Objects.requireNonNull(
                            adapterRuntime.observeAdapter(registration.identity()),
                            "adapter observation");
                    if (currentObservation.isEmpty()) {
                        continue;
                    }
                    AdapterArtifactObservation observation = currentObservation.orElseThrow();
                    if (!registration.identity().equals(observation.identity())
                            || !observation.exists()
                            || !observation.active()) {
                        continue;
                    }

                    addCandidate(candidate, candidatesByKey, candidates);
                }
            }
        }

        return new RuntimeCatalog(
                Map.copyOf(runtimesByProvider),
                Map.copyOf(candidatesByKey),
                List.copyOf(candidates));
    }

    private static void addCandidate(
            ModelRuntimeCandidate candidate,
            Map<String, ModelRuntimeCandidate> candidatesByKey,
            List<ModelRuntimeCandidate> candidates) {
        if (candidatesByKey.putIfAbsent(candidate.key(), candidate) != null) {
            throw new IllegalStateException("duplicate runtime candidate key: " + candidate.key());
        }
        candidates.add(candidate);
    }

    private LocalInferenceResult evaluate(LocalInferenceResult result) {
        for (LocalInferenceEvaluationHook hook : evaluationHooks) {
            hook.evaluate(result);
        }
        return result;
    }

    private record RuntimeCatalog(
            Map<String, SyntraModelRuntime> runtimesByProvider,
            Map<String, ModelRuntimeCandidate> candidatesByKey,
            List<ModelRuntimeCandidate> candidates) {
    }
}
