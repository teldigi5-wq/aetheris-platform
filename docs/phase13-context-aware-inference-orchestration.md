# Phase 13 Slice 5 — Context-Aware Local Inference Orchestration

## Certified baseline

Canonical development baseline: `170420dbc710f119160d77e414ca8a66d5bfd23b`.

That Slice 4 merge was observed with **35/35 push-triggered workflows successful** before Slice 5 was opened.

## Objective

Connect the certified scoped-context path to the existing Syntra model router and local runtime contract without creating a second inference stack or weakening the Phase 12 authority boundary.

The Slice 5 path is:

`ContextAssemblyRequest -> ContextPack -> ModelRouteRequest -> SyntraModelRouter -> ModelInvocation -> SyntraModelRuntime`

## Local-only execution boundary

`SyntraContextAwareInferenceOrchestrator` intentionally exposes only local runtime candidates to routing. The route request is always private, zero-cost, streaming-required and local-preferred. A remote execution target is rejected even if a future router implementation were to return one.

This slice therefore does not introduce cloud fallback, paid inference, public inference endpoints, or a new network client. Provider transport remains delegated to an already-registered `SyntraModelRuntime` such as the existing bounded Ollama adapter.

## Task-aware routing

Each `ContextAwareInferenceRequest` declares the model capabilities required by the task. Those requirements are passed into the existing `SyntraModelRouter`, along with the caller-supplied hardware evidence, context-window floor and explicit CPU-fallback decision.

Routing remains evidence-based. A target hardware profile is not treated as physically observed hardware, and this slice makes no owner-PC GPU, VRAM, throughput or latency claim.

## Context and citation continuity

Context is assembled before model selection. The selected `ContextPack` creates the `ModelInvocation`, and the invocation must carry the exact ordered `aetheris-memory://...` citation list from the pack.

The final `ContextAwareInferenceResult` carries the same immutable citation list. Model-generated text cannot add a trusted citation to this metadata path. If the citation chain changes before invocation, orchestration fails closed.

Retrieved evidence remains untrusted reference data. It does not become tool authority, approval, policy, connector permission, or authorization merely because a local model receives it.

## Streaming and cancellation

The orchestrator delegates cooperative cancellation through the existing `BooleanSupplier` runtime contract.

Every emitted `ModelStreamChunk` is checked for exact zero-based sequence continuity. Chunks after a terminal chunk and sequence gaps fail closed. A non-cancelled runtime must emit a terminal chunk; a cooperatively cancelled runtime may return without inventing terminal evidence.

## Structural evaluation hook

Slice 5 emits `ContextAwareInferenceEvidence` after a completed or cooperatively cancelled runtime call. It records only observed structural facts:

- selected context evidence count;
- citation count;
- emitted stream chunk count;
- whether a terminal chunk was observed;
- whether cancellation was observed; and
- whether stream sequence continuity was maintained.

The optional evaluation consumer is a hook for later measurement. These fields are not answer-quality, hallucination, factuality, semantic retrieval-quality, or benchmark scores.

## Acceptance evidence

`Phase13ContextAwareInferenceOrchestrationTest` proves that:

1. context is assembled before inference and exact citations reach both `ModelInvocation` and the result;
2. remote candidates cannot become a fallback in this local-only orchestration path;
3. task capability requirements affect model selection;
4. cooperative cancellation stops streaming without fabricating terminal evidence;
5. stream sequence discontinuity fails closed; and
6. structural evaluation evidence reflects the observed execution.

The dedicated workflow runs the focused Maven proof and verifies that Slice 5 delegates transport to the runtime interface rather than introducing another HTTP client.

## Truth boundary

This slice does **not** claim that:

- a model is installed on the owner's PC;
- GPU or VRAM capacity has been physically observed unless supplied as observed hardware evidence;
- TTFT, tokens/sec, RAM, VRAM, context limits or crash rates have been measured on owner hardware;
- the Slice 4 UTF-8 context-budget accounting is an exact model tokenizer count;
- retrieved context improves semantic answer quality;
- prompt injection is solved;
- model output is automatically factual; or
- generated text can bypass existing policy, approval, connector, tool-authority or audit controls.

## Next dependency

After the exact Slice 5 PR head is fully green and its canonical merge SHA is certified, the next Phase 13 slice should build response-grounding/evaluation continuity on top of this orchestration path without overstating semantic quality or owner-hardware performance.
