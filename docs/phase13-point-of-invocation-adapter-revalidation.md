# Phase 13 Slice 12 — Point-of-Invocation Adapter Revalidation

## Certified baseline

Canonical development baseline: `235bcddfa97350670a64de8c1d8e2daa926b023f`.

Before Slice 12 was created, the Slice 11 canonical merge SHA was observed with **42/42 push-triggered workflows successful**, zero failures, and no workflows still running.

## Objective

Reduce the stale-state window between Slice 11 catalog admission and the actual local model stream call.

Slice 11 already requires a fresh read-only adapter observation before a verified registration enters the routing catalog. Slice 12 adds a second observation after routing, after the pre-stream cancellation check, and immediately before `SyntraModelRuntime.stream(...)` is allowed to run.

## Two observation boundaries

For an adapter-derived candidate to execute, the same exact `AdapterRuntimeRegistration.identity()` must pass both checks:

1. **Catalog admission** — the artifact exists, is active, and its observed identity exactly matches the previously verified registration.
2. **Point of invocation** — the exact identity is observed again after the route is selected and immediately before local streaming begins.

The accepted adapter registration is retained in the runtime catalog by candidate key, so the second check is bound to the exact candidate selected by the unchanged model router.

## Fail-closed behavior

If the second observation is absent, detached, missing, or identity-drifted:

- the selected adapter is not invoked;
- no model stream chunks are accepted;
- the result is typed `UNAVAILABLE`;
- existing context evidence addresses are preserved; and
- the result records the deterministic rejection reason `selected adapter lost verified-active state immediately before invocation`.

Slice 12 does not silently fall back to another model after a selected adapter fails this final check. A future retry may build a fresh catalog and route again, but this invocation remains fail-closed and auditable.

## Cancellation continuity

Cancellation is checked before the second adapter observation. If the caller has already cancelled, the existing typed `CANCELLED` result is returned and the runtime is never streamed. This avoids performing an unnecessary point-of-invocation observation when no invocation will occur.

## Router and authority continuity

Slice 12 does not change `SyntraModelRouter` scoring, capability matching, hardware policy, zero-cost policy, locality rules, streaming sequence checks, or evaluation hooks.

It does not activate, detach, install, download, train, mutate adapter files, launch subprocesses, access credentials, call connectors/tools/browser/computer-use systems, or create new approval authority. Slice 9 lifecycle governance and the existing Stage 32/33 authority boundaries remain authoritative for consequential effects.

## Truth boundary

Hosted CI proves the orchestration contract with fake in-memory runtime observations. It does **not** prove that:

- a real adapter is installed or active on the owner's PC;
- the second observation and actual runtime execution are physically atomic;
- an external runtime cannot change state in the tiny interval after observation and before its `stream(...)` implementation begins;
- a concrete Ollama or other owner-PC bridge currently implements an atomic adapter invocation lease;
- owner hardware GPU, VRAM, RAM, drivers, CUDA, TTFT, tokens/sec, thermals, semantic quality, or failure rate have been measured.

Therefore Slice 12 materially narrows the time-of-check/time-of-use window but does not claim cryptographic or runtime-level atomicity. Closing that final physical-runtime gap requires a concrete adapter-aware runtime bridge or invocation lease/handle whose validation and stream start are coupled at the runtime point of effect.

## Acceptance evidence

`Phase13PointOfInvocationAdapterRevalidationTest` proves that:

1. a healthy adapter is observed at both catalog admission and point of invocation before streaming;
2. detach after routing returns `UNAVAILABLE` and produces zero stream calls;
3. artifact disappearance after routing returns `UNAVAILABLE` and produces zero stream calls;
4. identity drift after routing returns `UNAVAILABLE` and produces zero stream calls;
5. context evidence survives the fail-closed result; and
6. pre-stream cancellation short-circuits before the second observation and before streaming.

The existing Slice 10 and Slice 11 proofs are also run by the Slice 12 workflow to preserve registration and live-reconciliation compatibility.

## Next dependency

After the exact Slice 12 PR head and canonical merge SHA are fully certified, the next Phase 13 slice may introduce a typed adapter invocation lease/handle or concrete owner-PC adapter runtime bridge that couples final identity validation to runtime execution without weakening the current authority and evidence boundaries.
