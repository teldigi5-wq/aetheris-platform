package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Phase13ContextAwareInferenceOrchestrationTest {

    @Test
    void assemblesContextRoutesLocallyAndPreservesExactCitationEvidence() {
        ContextAssemblyRequest contextRequest = contextRequest();
        ContextPack context = contextPack();
        SyntraContextAssemblyService assembly = mock(SyntraContextAssemblyService.class);
        when(assembly.assemble(contextRequest)).thenReturn(context);

        RecordingRuntime local = new RecordingRuntime(
                "ollama",
                List.of(candidate("ollama", "qwen-coder", Set.of(ModelCapability.CHAT, ModelCapability.CODING), true)),
                List.of(new ModelStreamChunk(0, "bounded ", false),
                        new ModelStreamChunk(1, "answer", false),
                        new ModelStreamChunk(2, "", true)));
        RecordingRuntime remote = new RecordingRuntime(
                "cloud",
                List.of(candidate("cloud", "premium", Set.of(ModelCapability.CHAT, ModelCapability.CODING), false)),
                List.of(new ModelStreamChunk(0, "must-not-run", true)));

        SyntraContextAwareInferenceOrchestrator orchestrator =
                new SyntraContextAwareInferenceOrchestrator(assembly, List.of(remote, local));
        List<ModelStreamChunk> observed = new ArrayList<>();
        List<ContextAwareInferenceEvidence> evaluations = new ArrayList<>();

        ContextAwareInferenceResult result = orchestrator.stream(
                request(contextRequest, Set.of(ModelCapability.CODING)),
                observed::add,
                () -> false,
                evaluations::add);

        verify(assembly).assemble(contextRequest);
        assertEquals("ollama", result.route().providerId());
        assertEquals("qwen-coder", result.route().modelId());
        assertFalse(result.route().executionTarget() == ExecutionTarget.REMOTE);
        assertSame(context, result.context());
        assertEquals(context.citations(), result.citations());
        assertEquals(context.citations(), local.lastInvocation.evidenceAddresses());
        assertTrue(local.lastInvocation.input().contains(context.citations().getFirst()));
        assertEquals("bounded answer", result.output());
        assertEquals(observed, result.chunks());
        assertEquals(1, evaluations.size());
        assertTrue(evaluations.getFirst().terminalObserved());
        assertTrue(evaluations.getFirst().sequenceContinuous());
        assertFalse(remote.invoked);
    }

    @Test
    void requiredTaskCapabilitiesDriveModelSelection() {
        ContextAssemblyRequest contextRequest = contextRequest();
        SyntraContextAssemblyService assembly = mock(SyntraContextAssemblyService.class);
        when(assembly.assemble(contextRequest)).thenReturn(contextPack());

        RecordingRuntime runtime = new RecordingRuntime(
                "ollama",
                List.of(
                        candidate("ollama", "chat-only", Set.of(ModelCapability.CHAT), true),
                        candidate("ollama", "coder", Set.of(ModelCapability.CHAT, ModelCapability.CODING), true)),
                List.of(new ModelStreamChunk(0, "code", false), new ModelStreamChunk(1, "", true)));

        ContextAwareInferenceResult result = new SyntraContextAwareInferenceOrchestrator(assembly, List.of(runtime))
                .stream(request(contextRequest, Set.of(ModelCapability.CODING)), chunk -> { }, () -> false);

        assertEquals("coder", result.route().modelId());
        assertEquals("coder", runtime.lastInvocation.modelId());
    }

    @Test
    void cooperativeCancellationStopsTheStreamWithoutInventingTerminalEvidence() {
        ContextAssemblyRequest contextRequest = contextRequest();
        SyntraContextAssemblyService assembly = mock(SyntraContextAssemblyService.class);
        when(assembly.assemble(contextRequest)).thenReturn(contextPack());
        RecordingRuntime runtime = new RecordingRuntime(
                "ollama",
                List.of(candidate("ollama", "qwen-coder", Set.of(ModelCapability.CODING), true)),
                List.of(new ModelStreamChunk(0, "first", false),
                        new ModelStreamChunk(1, "second", false),
                        new ModelStreamChunk(2, "", true)));
        AtomicBoolean cancelled = new AtomicBoolean(false);
        List<ModelStreamChunk> observed = new ArrayList<>();

        ContextAwareInferenceResult result = new SyntraContextAwareInferenceOrchestrator(assembly, List.of(runtime))
                .stream(
                        request(contextRequest, Set.of(ModelCapability.CODING)),
                        chunk -> {
                            observed.add(chunk);
                            cancelled.set(true);
                        },
                        cancelled::get);

        assertEquals(1, observed.size());
        assertEquals("first", result.output());
        assertTrue(result.executionEvidence().cancelled());
        assertFalse(result.executionEvidence().terminalObserved());
        assertEquals(contextPack().citations(), result.citations());
    }

    @Test
    void streamSequenceDiscontinuityFailsClosed() {
        ContextAssemblyRequest contextRequest = contextRequest();
        SyntraContextAssemblyService assembly = mock(SyntraContextAssemblyService.class);
        when(assembly.assemble(contextRequest)).thenReturn(contextPack());
        RecordingRuntime broken = new RecordingRuntime(
                "ollama",
                List.of(candidate("ollama", "qwen-coder", Set.of(ModelCapability.CODING), true)),
                List.of(new ModelStreamChunk(1, "out-of-order", true)));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new SyntraContextAwareInferenceOrchestrator(assembly, List.of(broken))
                        .stream(request(contextRequest, Set.of(ModelCapability.CODING)), chunk -> { }, () -> false));

        assertTrue(failure.getMessage().contains("sequence discontinuity"));
    }

    @Test
    void remoteOnlyCandidatesFailClosedInsteadOfBecomingFallback() {
        ContextAssemblyRequest contextRequest = contextRequest();
        SyntraContextAssemblyService assembly = mock(SyntraContextAssemblyService.class);
        when(assembly.assemble(contextRequest)).thenReturn(contextPack());
        RecordingRuntime remote = new RecordingRuntime(
                "cloud",
                List.of(candidate("cloud", "premium", Set.of(ModelCapability.CODING), false)),
                List.of(new ModelStreamChunk(0, "remote", true)));

        InferenceRoutingException failure = assertThrows(
                InferenceRoutingException.class,
                () -> new SyntraContextAwareInferenceOrchestrator(assembly, List.of(remote))
                        .stream(request(contextRequest, Set.of(ModelCapability.CODING)), chunk -> { }, () -> false));

        assertTrue(failure.rejectionReasons().contains("NO_CANDIDATES"));
        assertFalse(remote.invoked);
    }

    private static ContextAwareInferenceRequest request(
            ContextAssemblyRequest contextRequest,
            Set<ModelCapability> capabilities) {
        return new ContextAwareInferenceRequest(
                contextRequest,
                "Use only the scoped evidence to answer.",
                capabilities,
                new HardwareCapacity(16 * 1024L, 6 * 1024L, true, HardwareEvidence.TARGET_PROFILE),
                512,
                256,
                true);
    }

    private static ContextAssemblyRequest contextRequest() {
        return new ContextAssemblyRequest(
                RetrievalScope.project("aetheris-platform"),
                "how should local inference use scoped context?",
                5,
                ContextBudget.safeDefaults());
    }

    private static ContextPack contextPack() {
        RetrievalScope scope = RetrievalScope.project("aetheris-platform");
        UUID nodeId = UUID.fromString("00000000-0000-0000-0000-000000000135");
        RetrievalEvidence source = new RetrievalEvidence(
                "aetheris-memory://project/aetheris-platform/" + nodeId,
                nodeId,
                scope,
                "inference#local",
                "Local inference must preserve scoped citation identity.",
                0.93,
                "VECTOR",
                Instant.parse("2026-09-20T16:00:00Z"));
        ContextEvidence evidence = new ContextEvidence(source, source.excerpt());
        ContextQualityAssessment quality = new ContextQualityAssessment(1, 1, 1, 0, 0, 0.93, 0.93, 0.93);
        return new ContextPack(
                scope,
                "how should local inference use scoped context?",
                List.of(evidence),
                List.of(evidence.citation()),
                evidence.estimatedTokens(),
                quality,
                Instant.parse("2026-09-20T16:01:00Z"));
    }

    private static ModelRuntimeCandidate candidate(
            String provider,
            String model,
            Set<ModelCapability> capabilities,
            boolean local) {
        return new ModelRuntimeCandidate(
                provider,
                model,
                capabilities,
                8192,
                local,
                true,
                true,
                local,
                local ? 4096 : 0,
                local ? 8192 : 0,
                local,
                local ? 0.88 : 0.99,
                local ? 250 : 100,
                local ? 45 : 90,
                0.01,
                local ? 3900 : 0,
                local ? 0 : 0.05);
    }

    private static final class RecordingRuntime implements SyntraModelRuntime {
        private final String providerId;
        private final List<ModelRuntimeCandidate> models;
        private final List<ModelStreamChunk> chunks;
        private ModelInvocation lastInvocation;
        private boolean invoked;

        private RecordingRuntime(
                String providerId,
                List<ModelRuntimeCandidate> models,
                List<ModelStreamChunk> chunks) {
            this.providerId = providerId;
            this.models = List.copyOf(models);
            this.chunks = List.copyOf(chunks);
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public List<ModelRuntimeCandidate> models() {
            return models;
        }

        @Override
        public void stream(
                ModelInvocation invocation,
                Consumer<ModelStreamChunk> sink,
                BooleanSupplier cancellationRequested) {
            invoked = true;
            lastInvocation = invocation;
            for (ModelStreamChunk chunk : chunks) {
                if (cancellationRequested.getAsBoolean()) {
                    return;
                }
                sink.accept(chunk);
            }
        }
    }
}
