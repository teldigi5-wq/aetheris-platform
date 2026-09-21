# Phase 13 Slice 13 — Typed Adapter Invocation Lease

## Certified baseline

Canonical development baseline: `cc4a1beff031060fbe385a8b0ee07460c743b47f`.

Before Slice 13 was created, the Slice 12 canonical merge SHA was observed with **43/43 push-triggered workflows successful**, zero failures, and no workflows still running.

## Objective

Strengthen the final adapter execution boundary introduced by Slices 10–12.

Slice 10 admitted only verified-active adapter registrations into the routing catalog. Slice 11 reconciled those registrations against fresh read-only runtime observations. Slice 12 repeated the exact observation immediately before streaming. Slice 13 now requires the selected adapter runtime to issue a typed invocation lease bound to the exact artifact identity, provider, and adapter-derived model identity, and adapter execution may proceed only through a lease-aware stream method.

## Typed lease contract

`AdapterInvocationLease` binds:

- the exact `AdapterArtifactIdentity`;
- the runtime `providerId`;
- the exact adapter-derived `modelId`; and
- an evidence address in the `aetheris-adapter-lease://` namespace.

The lease constructor rejects a model ID that does not equal `AdapterRuntimeRegistration.modelIdFor(identity)`. The orchestrator additionally checks that the lease exactly matches the selected registration, selected provider, selected model, and `ModelInvocation.modelId()`.

The evidence address is a typed traceability field. It is not a credential, capability token, cryptographic attestation, or proof of physical runtime atomicity.

## Explicit runtime opt-in

`AdapterInvocationLeasingRuntime` is an optional extension of `AdapterAwareSyntraModelRuntime`.

A runtime that only implements the older adapter-aware contract remains source-compatible, but an adapter selected from that runtime is now fail-closed before execution with the deterministic rejection:

`selected adapter runtime does not support invocation leases`

This prevents an older adapter-aware implementation from silently bypassing the stronger invocation boundary.

## Invocation sequence

For an adapter-derived selection, `SyntraLocalInferenceOrchestrator` now requires:

1. Slice 11 catalog-time observation: exact identity, exists, active;
2. routing through the unchanged `SyntraModelRouter`;
3. existing pre-stream cancellation handling;
4. runtime implements `AdapterInvocationLeasingRuntime`;
5. Slice 12 point-of-invocation observation: exact identity, exists, active;
6. runtime returns a non-empty `AdapterInvocationLease`;
7. lease exactly matches registration/provider/model/invocation identity; and
8. streaming starts only through `streamWithAdapterLease(...)`.

The ordinary `SyntraModelRuntime.stream(...)` path remains unchanged for non-adapter model selections.

## Fail-closed outcomes

Adapter execution returns typed `UNAVAILABLE` without streaming when:

- the selected runtime lacks lease support;
- the final Slice 12 observation is stale or missing;
- lease acquisition returns empty; or
- the lease does not exactly match the selected registration and route.

Context evidence addresses remain preserved on these typed failures.

Slice 13 intentionally does not silently reroute inside the same inference after a lease failure. A future retry may construct a fresh catalog and route again.

## Cancellation continuity

The existing cancellation check remains before final observation and lease acquisition. A request already cancelled returns typed `CANCELLED` without acquiring a lease or invoking either stream path.

## Security and authority boundary

Slice 13 introduces no consequential-action authority. It does not:

- activate or detach adapters;
- install, download, train, or mutate adapter/model artifacts;
- launch subprocesses or shells;
- add filesystem mutation;
- perform network calls;
- access credentials;
- call tools, connectors, browser, or computer-use systems; or
- mint or bypass Stage 32/33 approvals.

The lease is scoped to local model invocation only and grants no external-action authority.

## Truth boundary

Hosted CI proves the typed orchestration contract with fake in-memory runtimes only.

It does **not** prove that:

- a concrete owner-PC runtime currently implements this lease contract;
- a lease is cryptographically unforgeable;
- lease acquisition and physical model execution are atomic inside a real runtime;
- an adapter cannot change externally between lease issuance and a fake/runtime implementation beginning execution;
- a real adapter is installed, active, loaded, or serving inference on the owner's PC; or
- owner hardware GPU, VRAM, RAM, drivers, CUDA, TTFT, tokens/sec, thermals, semantic quality, or failure rate have been measured.

Therefore Slice 13 closes the orchestration-side bypass: an adapter cannot execute through the normal stream path. Physical atomicity still requires a concrete owner-PC runtime implementation that treats lease issuance and lease-aware stream start as one runtime-controlled point-of-effect boundary.

## Acceptance evidence

`Phase13AdapterInvocationLeaseTest` proves that:

1. an exact lease permits adapter streaming only through the lease-aware path;
2. an adapter-aware runtime without lease support fails closed;
3. an empty lease fails closed with zero stream calls;
4. a provider-mismatched lease fails closed with zero stream calls;
5. pre-lease cancellation preserves existing cancellation semantics;
6. ordinary base-model inference still uses the existing normal stream path; and
7. lease construction rejects model-identity drift and an invalid evidence namespace.

The Slice 13 workflow also reruns the focused Slice 10, Slice 11, and Slice 12 adapter proofs to preserve the full registration-to-invocation continuity chain.

## Next dependency

After the exact Slice 13 PR head and canonical merge SHA are fully certified, the next slice may implement a concrete local runtime bridge that consumes this lease contract. Any claim of owner-PC installation, runtime atomicity, or performance remains blocked until exercised and measured on the real target runtime and hardware.
