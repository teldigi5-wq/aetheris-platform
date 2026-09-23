# Phase 13 Slice 5 — Context-Aware Local Inference Orchestration

## Certified baseline

Canonical development baseline: `170420dbc710f119160d77e414ca8a66d5bfd23b`.

Before this slice was opened, the final Slice 4 PR head `a26ed7fb20ee8a249bd263eae2e078c91e7cd94c` was observed with **32/32 pull-request workflows successful**, and the canonical Slice 4 merge SHA was observed with **35/35 push-triggered workflows successful**.

## Objective

Connect the typed scoped context produced by Slice 4 to the existing local model routing and streaming runtime without duplicating the Ollama adapter, inventing remote inference behavior, or granting model output execution authority.

The path is now:

`ContextPack -> ContextAwareInferenceRequest -> SyntraModelRouter -> SyntraModelRuntime -> LocalInferenceResult`

## Task-aware routing

`InferenceTask` maps an explicit task intent to the capabilities already understood by the model router:

- `CHAT` requires chat capability;
- `CODING` requires chat and coding capability;
- `REASONING` requires chat and reasoning capability;
- `TOOL_PLANNING` requires chat and tool-calling capability, but remains planning text only.

`ContextAwareInferenceRequest` always creates a route request with zero-cost mode, private mode, streaming required, and local preference enabled. The caller may permit CPU fallback, but this slice does not permit remote fallback.

The caller supplies `requiredContextWindowTokens`. Slice 5 deliberately does not invent a tokenizer conversion from the Slice 4 UTF-8 evidence accounting. Provider-specific tokenizer accounting remains future work.

## Exact local-runtime handoff

`SyntraLocalInferenceOrchestrator` gathers the current candidates from configured `SyntraModelRuntime` instances, rejects duplicate provider or candidate identities, delegates selection to `SyntraModelRouter`, and then resolves the selected provider/model back to the exact runtime.

A route that somehow resolves to a remote candidate or `ExecutionTarget.REMOTE` fails closed even though private routing should already exclude it. This is defense in depth rather than a new remote-inference surface.

## Context and citation continuity

The selected model ID is passed to `ContextPack.toModelInvocation(...)`. The resulting invocation retains the exact ordered `aetheris-memory://...` evidence addresses from Slice 4.

`LocalInferenceResult` carries those same evidence addresses for completed, cancelled, and unavailable outcomes. It does not claim that a model actually used every supplied evidence item; it records the evidence made available to the invocation.

## Streaming and cancellation continuity

The orchestration layer validates provider-independent stream structure:

1. chunk sequence numbers must start at zero and remain contiguous;
2. no chunk may appear after a terminal frame;
3. a normal non-cancelled stream must produce a terminal frame; and
4. cancellation may return partial output, but it is explicitly marked `CANCELLED` and never rewritten as completed output.

The existing runtime retains responsibility for transport-level bounds and protocol handling. Slice 5 does not add an HTTP client.

## Evaluation hooks

`LocalInferenceEvaluationHook` receives the immutable `LocalInferenceResult` after a completed, cancelled, or unavailable orchestration outcome. The hook is an observation/evaluation extension point only. It does not receive tool authority, connector credentials, approval state, or an execution callback.

This slice does not claim semantic answer quality, hallucination rate, benchmark accuracy, or model superiority. Future evaluation work may consume these hooks and produce measured evidence.

## Security and authority boundary

Model output remains text. `TOOL_PLANNING` means that a selected model can be capable of producing tool-shaped plans; it does not execute a tool and does not bypass Phase 12 policy, approval, audit, or connector-account continuity controls.

The dedicated proof rejects new HTTP-client usage in the orchestration files and rejects imports from the tool, connector, or approval packages. Consequential actions remain outside this local inference slice.

## Acceptance evidence

`Phase13ContextAwareLocalInferenceTest` proves that:

1. coding tasks route through the existing model router to a compatible local runtime;
2. exact context citations survive into the model invocation and final result;
3. streamed output is assembled only from contiguous chunks and requires terminal completion;
4. cancellation returns explicit partial cancelled output without inventing a terminal completion;
5. remote-only or paid-only candidates are unavailable under the enforced private/zero-cost route policy;
6. evaluation hooks observe typed outcomes; and
7. tool-planning capability changes model requirements only and creates no execution-authority surface.

## Truth boundary

This slice does not claim that:

- local model weights are installed on the owner PC;
- owner-PC GPU, VRAM, RAM, TTFT, tokens/sec, thermal behavior, or failure rate has been measured;
- Slice 4 UTF-8 context units equal a specific model tokenizer count;
- model output is factually correct or citation-faithful merely because citations were supplied;
- prompt injection is solved;
- remote inference has been implemented here; or
- model output can authorize or execute consequential actions.

Owner-hardware performance certification must remain blocked until it is measured on the actual target machine.
