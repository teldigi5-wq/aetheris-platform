# Phase 13 Slice 14 — Bound Adapter Invocation Handle

## Certified baseline

Slice 14 starts from the certified Slice 13 canonical merge SHA `cd05d6677de201c7121d4d89da232a2545726163`.

That SHA was observed with 44/44 push-triggered workflows successful after an exact-SHA rerun confirmed a one-off deployment/rollback harness failure was transient. No code or CI weakening was used to obtain certification.

## Objective

Close the remaining orchestration-side gap between issuance of the Slice 13 `AdapterInvocationLease` and the call that begins adapter streaming, while preserving the already-certified Slice 10–13 runtime contracts.

Slice 13 required an exact runtime-issued lease and prevented adapter execution through the ordinary model stream path. Slice 14 ensures every adapter stream start is mediated by a single-use `AdapterRuntimeInvocationHandle` bound to that exact lease.

## Bound handle contract

`AdapterRuntimeInvocationHandle` contains:

- the exact `AdapterInvocationLease`;
- the stream executor bound to that lease; and
- a thread-safe single-use guard.

Before execution the handle rejects model identity drift between the `ModelInvocation` and bound lease. A second stream attempt is rejected deterministically.

The handle is an in-process orchestration contract. It is not a credential, capability token, cryptographic attestation, or proof that external model state cannot change.

## Native handle support and Slice 13 compatibility

`AdapterInvocationHandleRuntime` is an optional stronger extension of the Slice 13 `AdapterInvocationLeasingRuntime` contract. A runtime implementing it may issue its own `AdapterRuntimeInvocationHandle` for the exact verified registration.

A certified Slice 13 runtime that implements only `AdapterInvocationLeasingRuntime` remains compatible. After the existing fresh point-of-invocation observation, the orchestrator acquires and validates the exact Slice 13 lease and wraps that lease plus `streamWithAdapterLease(...)` in the same single-use handle type.

This compatibility path does not bypass Slice 13. Missing or mismatched leases retain the existing fail-closed outcomes, and ordinary `runtime.stream(...)` is still unavailable to adapter-derived selections.

Older non-adapter `SyntraModelRuntime` implementations remain unchanged.

## Invocation sequence

For an adapter-derived route, `SyntraLocalInferenceOrchestrator` requires:

1. catalog-time verified-active adapter observation;
2. unchanged local-only model routing;
3. pre-invocation cancellation check;
4. Slice 13 lease-aware runtime support;
5. fresh point-of-invocation verified-active observation;
6. either a native runtime-issued handle or an exact runtime-issued Slice 13 lease;
7. exact lease continuity across artifact identity, provider, selected model, and `ModelInvocation.modelId()`;
8. a not-yet-started handle; and
9. streaming through `AdapterRuntimeInvocationHandle.stream(...)` only.

For the compatibility path, the orchestrator constructs the handle only after the exact lease passes the same Slice 13 validation. It never falls back to ordinary model streaming for an adapter selection.

## Fail-closed outcomes

Adapter execution returns typed `UNAVAILABLE` without streaming when:

- the runtime lacks Slice 13 lease support;
- point-of-invocation adapter observation is stale or missing;
- a native handle runtime returns no handle;
- a native handle carries a mismatched lease or is already started;
- a lease-only runtime returns no lease; or
- a lease-only runtime returns a lease that does not match the exact selected registration/route/invocation.

Context evidence addresses remain preserved on these failures.

## Cancellation and base-model continuity

Cancellation remains checked before final adapter observation and handle/lease acquisition. A pre-cancelled request does not acquire or start a handle.

Ordinary base-model inference remains on the existing `SyntraModelRuntime.stream(...)` path.

## Security and authority boundary

Slice 14 remains local orchestration contract work only. It introduces no authority to:

- activate, detach, train, install, download, or mutate model/adapter artifacts;
- launch subprocesses or shells;
- write/delete/move filesystem artifacts;
- make HTTP/network calls;
- access credentials;
- invoke tools, connectors, browser, or computer-use systems; or
- mint or bypass Stage 32/33 approvals.

## Truth boundary

Hosted CI proves the contract with fake in-memory runtimes. It does **not** prove that:

- Ollama, llama.cpp, vLLM, or another concrete local provider is integrated;
- a real adapter or model is installed or loaded;
- handle creation and physical GPU execution are atomic in a concrete provider;
- the handle is cryptographically unforgeable;
- owner-PC GPU/VRAM/RAM/drivers/CUDA are available; or
- TTFT, tokens/sec, memory use, thermals, semantic quality, or failure rates have been measured.

The lease-only compatibility wrapper strengthens orchestration-side single-use mediation; it does not turn two runtime calls into physical provider atomicity. Those stronger claims remain blocked until a concrete local runtime bridge exists and is exercised on the real target machine.

## Acceptance evidence

`Phase13BoundAdapterInvocationHandleTest` proves:

1. a native exact bound handle owns the adapter stream start;
2. a Slice 13 lease-only runtime remains compatible and is mediated through a single-use handle;
3. an empty native handle fails closed;
4. a provider-mismatched native handle fails closed;
5. a handle is single-use and rejects invocation model drift;
6. pre-handle cancellation remains intact; and
7. ordinary base-model inference is unchanged.

The dedicated Slice 14 workflow also reruns the Slice 10–13 adapter continuity proofs so historical revalidation, reconciliation, lease failure, and routing guarantees cannot silently regress.

## Next dependency

After the exact Slice 14 PR head and canonical merge SHA are fully green, Slice 15 may introduce a concrete local-runtime bridge contract that owns provider discovery, local model inventory, adapter observation, lease/handle issuance, and streaming without yet claiming owner-machine installation or performance.
