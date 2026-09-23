# Phase 13 Slice 2 — Bounded Local Runtime Adapter

## Baseline

Canonical development baseline: `c880fcd7475fc5aa9576df01998ad2f0b50a64f7`.

That merge SHA was certified after Phase 13 Slice 1 with all 32 push-triggered workflows successful before this slice was opened.

## Objective

Put the first real local-provider transport behind `SyntraModelRuntime` without weakening the Phase 12 authority model, inventing hardware evidence, or allowing arbitrary network endpoints and arbitrary local model execution.

## What this slice adds

- `OllamaLocalRuntime`, a concrete loopback-only Ollama adapter behind `SyntraModelRuntime`;
- `/api/tags` provider/model discovery with typed, timestamped reachability evidence;
- `/api/generate` NDJSON streaming integration mapped into ordered `ModelStreamChunk` frames;
- cooperative cancellation that closes the HTTP response stream rather than inventing a successful terminal frame;
- explicit model allowlisting through configured `ModelRuntimeCandidate` profiles;
- hard request bounds for input characters, output tokens, stream chunks, per-chunk characters, and request timeout;
- `LocalRuntimeEndpoint`, which fails closed unless the endpoint is explicit HTTP loopback with a declared port and no credentials/query/fragment/path;
- `JvmLocalHardwareProbe`, which can observe repository-runner CPU count and physical RAM when the JVM exposes it;
- an explicit truth boundary where the JVM hardware probe does not claim GPU or VRAM evidence;
- a loopback HTTP integration test that exercises real request/response framing without requiring Ollama, model weights, CUDA, or internet access.

## Security and safety boundaries

The local runtime transport is deliberately narrow:

1. only `localhost`, `127.0.0.1`, or IPv6 loopback is accepted;
2. HTTPS and non-loopback hosts are rejected instead of silently expanding trust;
3. credentials, query strings, fragments, and base paths are rejected;
4. model execution is limited to explicitly configured model IDs;
5. request and stream sizes are bounded before or during execution;
6. non-2xx responses, malformed/incomplete streams, oversized frames, and runtime error frames fail closed;
7. cancellation stops consumption without emitting a fake terminal-success event.

These controls keep this slice from turning the model adapter into a general-purpose SSRF/network client or an unbounded local execution surface.

## Discovery truth boundary

Provider discovery proves only what the contacted loopback provider reports at that moment. A discovered model name is **not** treated as proof of quality, context length, memory fit, TTFT, tokens/sec, or reliability. Those measurements remain separate evidence and continue to flow through the existing benchmark/routing model.

`JvmLocalHardwareProbe` observes CPU count and, where available, physical RAM from the JVM/OS management surface. It deliberately reports `gpuEvidenceAvailable() == false`. This slice does not claim that CUDA works, that a discrete GPU exists, that a particular VRAM amount is free, or that the owner's planned RTX-class hardware has been physically tested.

## Acceptance evidence

`Phase13LocalRuntimeAdapterTest` proves that:

1. non-loopback and HTTPS local-runtime endpoints fail closed;
2. provider discovery is executed through an ephemeral loopback HTTP server and returns deterministic de-duplicated model IDs;
3. Ollama NDJSON frames are converted into monotonic `ModelStreamChunk` values with a real terminal frame;
4. the requested output-token bound is carried into the provider request;
5. cooperative cancellation stops stream consumption without inventing completion;
6. unconfigured model IDs are blocked before execution;
7. oversized input and output-token requests are rejected before provider execution;
8. repository hardware discovery reports CPU/RAM evidence without claiming GPU evidence.

## Non-goals

This slice does not:

- install or start Ollama;
- download model weights;
- claim that a model ran on the owner's PC;
- claim measured TTFT, tokens/sec, VRAM usage, thermal behavior, or crash rate;
- introduce RAG, persistent-memory retrieval, LoRA/QLoRA, or training;
- expose local inference over a remote-control endpoint;
- enable paid/cloud providers;
- change Phase 12 connector authority, OAuth continuity, audit, or Stage 33 governance.

## Next dependency

After this slice is exact-head green and the canonical merge SHA is certified, the next Phase 13 slice should add the retrieval foundation: bounded document ingestion, chunking, embedding-provider abstraction, evidence-addressable retrieval, and project/workspace scoping. Real owner-PC model benchmarking remains a separate hardware-dependent proof and must not be simulated as physical evidence.
