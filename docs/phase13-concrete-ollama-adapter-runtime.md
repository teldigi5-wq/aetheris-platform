# Phase 13 Slice 15 — Concrete Ollama Adapter Runtime Reconciliation

## Certified baseline

Slice 15 starts from certified Slice 14 canonical merge SHA `a951abc60f162a5cabc239f92089cce1e0495749`.

That SHA was observed with **45/45 push-triggered workflows successful**, zero failures, and no queued or in-progress workflows before this slice was opened.

## Objective

Close the concrete-runtime gap left intentionally by Slices 9–14 without duplicating the already-real Slice 2 Ollama transport or weakening the authority chain.

The existing `OllamaLocalRuntime` already provides loopback-only `/api/tags` discovery and bounded `/api/generate` streaming for ordinary local models. Slice 15 connects a **pre-provisioned, explicitly configured Ollama model alias** to the certified adapter path:

`Slice 9 governed activation -> verified lifecycle result -> Slice 15 reconciliation -> Slice 10 registration -> Slice 11/12 live observation -> Slice 13 lease -> Slice 14 bound handle -> Ollama loopback stream`

## Pre-provisioned binding contract

`OllamaAdapterBinding` binds:

- one exact `AdapterArtifactIdentity`;
- one explicit Ollama provider model alias; and
- one reserved Aetheris `AdapterRuntimeRegistration` routing candidate.

The provider alias must be distinct from the declared base model and must not use the reserved `aetheris-adapter-runtime://` namespace.

The routing candidate must remain `ollama-local`, local-only, zero-cost, and exactly bound to the approved artifact SHA-256 through the existing reserved Aetheris model ID.

The provider alias is configuration supplied by the owner/application. It is **not** proof that Ollama model bytes equal the logical adapter artifact hash. Slice 15 deliberately keeps that distinction explicit.

## Concrete bridge

`OllamaAdapterRuntimeBridge` implements:

- `LocalAdapterActivationPort`;
- `AdapterAwareSyntraModelRuntime` through the stronger lease/handle interfaces;
- `AdapterInvocationLeasingRuntime`; and
- `AdapterInvocationHandleRuntime`.

It composes the already-certified `OllamaLocalRuntime` rather than adding a second HTTP implementation.

All provider calls therefore retain Slice 2's explicit HTTP loopback endpoint restriction, configured request bounds, model allowlisting, NDJSON framing, cancellation behavior, and no arbitrary remote endpoint support.

## Activation truth

Slice 15 does **not** install, build, import, download, or physically load an adapter.

`activate(...)` means only: the explicitly bound Ollama alias is currently reported by the loopback provider and Aetheris marks that exact binding active in the current process.

`detach(...)` removes that process-local Aetheris eligibility. It does not delete the Ollama model alias or mutate provider files.

This is a real local Aetheris routing-state effect, but it is not a claim of provider-native LoRA hot-loading.

## Lifecycle reconciliation

Activation alone cannot publish an adapter route.

`reconcileLifecycle(...)` requires an already-produced Slice 9 lifecycle result. Only `ACTIVATED_VERIFIED` can construct an `AdapterRuntimeRegistration`, and the bound Ollama alias must still be present and active at reconciliation time.

`ROLLED_BACK_VERIFIED`, failed, blocked, or unverified lifecycle outcomes remove or withhold registration.

This preserves the Stage 32/33 authority chain: the concrete runtime bridge cannot mint `ACTIVATED_VERIFIED` truth and cannot bypass the owner approval consumed by Slice 9 governance.

## Live invocation continuity

For a reconciled adapter route:

1. the provider alias is observed during catalog assembly;
2. it is observed again at point-of-invocation by the existing orchestrator;
3. the bridge issues the exact Slice 13 `AdapterInvocationLease` only while the reconciled registration is current and active;
4. Slice 14 obtains a single-use bound invocation handle;
5. `streamWithAdapterLease(...)` performs another bridge-state/provider-presence check;
6. the logical reserved adapter model ID is translated to the pre-provisioned Ollama alias only inside the bound lease path; and
7. context evidence addresses are preserved into the translated provider invocation.

Ordinary `stream(...)` rejects reserved adapter model IDs, so an adapter route cannot bypass lease/handle mediation.

If the provider alias disappears after lifecycle reconciliation, the adapter fails closed before generation and loses route eligibility.

## Security / authority boundary

Slice 15 adds no authority to:

- download or install models;
- train LoRA/QLoRA adapters;
- execute shell commands or subprocesses;
- mutate arbitrary files;
- call non-loopback providers;
- access credentials;
- invoke tools, connectors, browser, or computer-use systems; or
- mint/bypass Stage 32 or Stage 33 approval.

The only network path remains the existing `LocalRuntimeEndpoint`-guarded loopback HTTP transport.

## Truth boundary

Hosted CI uses an ephemeral loopback HTTP server that speaks the Ollama-compatible tags/generate subset. It proves repository integration and protocol behavior; it does **not** prove that:

- Ollama is installed on the owner's PC;
- a real adapter/model alias exists on owner hardware;
- the configured alias is cryptographic proof of the logical adapter artifact bytes;
- LoRA/QLoRA training has run;
- Ollama natively hot-loads/detaches an adapter through this bridge;
- owner-PC GPU/VRAM/RAM/drivers/CUDA are available; or
- TTFT, tokens/sec, VRAM/RAM consumption, thermals, semantic quality, or failure rates have been measured.

Those physical and performance claims remain blocked until direct owner-machine validation.

## Acceptance evidence

`Phase13ConcreteOllamaAdapterRuntimeTest` proves that:

1. a present pre-provisioned alias can be activated process-locally without minting a routing registration;
2. a missing alias fails closed without inventing activation;
3. an exact `ACTIVATED_VERIFIED` lifecycle result can be reconciled into the existing adapter registration path;
4. end-to-end routing uses the logical adapter identity while the bound lease path translates only at stream start to the concrete Ollama alias;
5. context citations survive that translation;
6. alias disappearance after reconciliation fails closed before generation;
7. verified rollback removes registration and prevents lease acquisition;
8. unverified lifecycle truth cannot publish a route; and
9. unsafe/ambiguous provider alias bindings are rejected.

The Slice 15 workflow also reruns the existing Slice 2 and Slice 9–14 focused proofs.

## Next dependency

After the exact Slice 15 head and canonical merge SHA are fully green, the next slice should add **owner-machine runtime readiness evidence and deployment/configuration wiring** around this concrete bridge without claiming performance. Actual performance certification remains a later hardware-dependent gate.
