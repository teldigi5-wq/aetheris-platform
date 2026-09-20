# Phase 13 Slice 1 — Syntra-Core Runtime Foundation

## Baseline

Canonical development baseline: `309fda7e6c4bb3d172bd06452913b81638bddd85`.

Phase 12 is treated as closed before this slice begins. This slice starts Phase 13 without changing the Phase 12 authority, connector, OAuth, governance, or audit boundaries.

## Objective

Create the smallest provider-independent local-model runtime foundation that later Ollama/OpenAI-compatible adapters, RAG, memory, evaluation, and model orchestration can build on without making unsupported physical-hardware claims.

## What this slice adds

- `SyntraModelRuntime`, a provider-independent streaming runtime contract;
- typed invocation and stream-chunk contracts with cooperative cancellation supplied by the caller;
- typed model capabilities and runtime candidate evidence;
- `HardwareCapacity` with an explicit evidence boundary between a target profile and observed hardware;
- deterministic model routing across local GPU, local CPU fallback, and remote providers;
- hard filters for health, capability fit, context-window size, streaming support, Private Mode, Zero-Cost Mode, RAM, and VRAM;
- CPU fallback only when both the request and the candidate explicitly allow it;
- reuse of the existing Stage 31 `ModelBenchmarkEngine` rather than creating a competing benchmark/ranking subsystem;
- a small local-preference bias and explicit CPU-fallback penalty on top of the established benchmark score;
- deterministic rejection reasons so a later UI/telemetry layer can explain why a model was or was not eligible.

## Acceptance evidence

`Phase13SyntraCoreRuntimeFoundationTest` proves that:

1. Private Mode fails closed before a remote provider can be selected;
2. Zero-Cost Mode rejects priced providers;
3. a model too large for configured VRAM may use CPU only when fallback is explicitly allowed;
4. capability, context, and streaming requirements are hard eligibility filters;
5. the same evidence produces the same route decision;
6. a 16 GB RAM / 6 GB VRAM target profile is not represented as physically verified hardware;
7. the runtime contract supports streaming and cooperative cancellation without coupling Syntra-Core to one provider implementation.

## Truth boundary

This is repository evidence only. It does **not** prove that Ollama is installed, that an RTX 4050 is available, that CUDA works, that 6 GB VRAM is actually free, that a named model fits in memory, or that real TTFT/tokens-per-second measurements have been collected on the owner's PC.

`HardwareEvidence.TARGET_PROFILE` must remain non-verified. Only observed hardware evidence may report `physicallyVerified() == true`.

## Non-goals

This slice does not:

- download or execute a model;
- call Ollama or any cloud model provider;
- add model weights or training data;
- perform RAG or persistent-memory retrieval;
- claim physical GPU/CPU performance;
- change owner rules or Stage 33 governance;
- silently enable paid providers;
- expose a remote-control endpoint.

## Next dependency

The next Phase 13 slice should implement a bounded local runtime adapter and streaming protocol integration behind `SyntraModelRuntime`, plus repository-testable hardware/provider discovery. Real owner-PC benchmarking remains blocked until physical hardware is available and intentionally tested.
