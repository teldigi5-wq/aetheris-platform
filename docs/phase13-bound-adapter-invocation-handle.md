# Phase 13 Slice 14 — Bound Adapter Invocation Handle

## Certified baseline

Slice 14 starts from the certified Slice 13 canonical merge SHA `cd05d6677de201c7121d4d89da232a2545726163`.

That SHA was observed with 44/44 push-triggered workflows successful after an exact-SHA rerun confirmed a one-off deployment/rollback harness failure was transient. No code or CI weakening was used to obtain certification.

## Objective

Close the remaining orchestration-side gap between issuance of the Slice 13 `AdapterInvocationLease` and the call that begins adapter streaming.

Slice 13 required an exact runtime-issued lease and prevented adapter execution through the ordinary model stream path. Slice 14 binds that lease to a runtime-owned, single-use invocation handle and requires the orchestrator to start adapter streaming through the handle itself.

## Bound handle contract

`AdapterRuntimeInvocationHandle` contains:

- the exact `AdapterInvocationLease`;
- the runtime-provided stream executor bound to that lease; and
- a thread-safe single-use guard.

Before execution the handle rejects model identity drift between the `ModelInvocation` and bound lease. A second stream attempt is rejected deterministically.

The handle is an in-process orchestration contract. It is not a credential, capability token, cryptographic attestation, or proof that external model state cannot change.

## Runtime opt-in

`AdapterInvocationHandleRuntime` extends the Slice 13 `AdapterInvocationLeasingRuntime` contract and returns an `Optional<AdapterRuntimeInvocationHandle>` for the exact verified registration.

An adapter runtime that supports Slice 13 leases but not Slice 14 bound handles now fails closed with:

`selected adapter runtime does not support bound invocation handles`

Older non-adapter `SyntraModelRuntime` implementations remain unchanged.

## Invocation sequence

For an adapter-derived route, `SyntraLocalInferenceOrchestrator` now requires:

1. catalog-time verified-active adapter observation;
2. unchanged local-only model routing;
3. pre-invocation cancellation check;
4. Slice 13 lease support;
5. Slice 14 bound-handle support;
6. fresh point-of-invocation verified-active observation;
7. a non-empty runtime-issued bound handle;
8. exact handle lease continuity across artifact identity, provider, selected model, and `ModelInvocation.modelId()`;
9. a not-yet-started handle; and
10. streaming through `AdapterRuntimeInvocationHandle.stream(...)` only.

The orchestrator no longer calls `streamWithAdapterLease(...)` directly for adapter selections.

## Fail-closed outcomes

Adapter execution returns typed `UNAVAILABLE` without streaming when:

- the runtime lacks lease support;
- the runtime has lease support but lacks bound-handle support;
- point-of-invocation adapter observation is stale or missing;
- handle acquisition returns empty; or
- the handle's bound lease does not match the exact selected registration/route/invocation.

Context evidence addresses remain preserved on these failures.

## Cancellation and base-model continuity

Cancellation remains checked before final adapter observation and handle acquisition. A pre-cancelled request does not acquire or start a handle.

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
- handle issuance and physical GPU execution are atomic in a concrete provider;
- the handle is cryptographically unforgeable;
- owner-PC GPU/VRAM/RAM/drivers/CUDA are available; or
- TTFT, tokens/sec, memory use, thermals, semantic quality, or failure rates have been measured.

Those claims remain blocked until a concrete local runtime bridge exists and is exercised on the real target machine.

## Acceptance evidence

`Phase13BoundAdapterInvocationHandleTest` proves:

1. an exact bound handle owns the adapter stream start;
2. a lease-only runtime fails closed before lease acquisition or streaming;
3. an empty handle fails closed;
4. a provider-mismatched handle fails closed;
5. a handle is single-use and rejects invocation model drift;
6. pre-handle cancellation remains intact; and
7. ordinary base-model inference is unchanged.

The dedicated Slice 14 workflow also reruns the Slice 10–13 adapter continuity proofs.

## Next dependency

After the exact Slice 14 PR head and canonical merge SHA are fully green, Slice 15 may introduce a concrete local-runtime bridge contract that owns provider discovery, local model inventory, adapter observation, lease/handle issuance, and streaming without yet claiming owner-machine installation or performance.
