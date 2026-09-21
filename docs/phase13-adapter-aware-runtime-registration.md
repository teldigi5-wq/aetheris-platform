# Phase 13 Slice 10 — Adapter-Aware Runtime Registration

## Certified baseline

Canonical development baseline: `1f9f2793e0fcb2a789ed82ead78ab4e5165ca12b`.

Before Slice 10 was created, the canonical Slice 9 merge SHA was observed with **40/40 push-triggered workflows successful**, zero failures, and no workflows still running.

## Objective

Allow an adapter artifact that has already reached Slice 9 `ACTIVATED_VERIFIED` truth to appear in the local model-routing catalog without weakening the existing model router, authority model, evidence continuity, cancellation semantics, streaming checks, or local/zero-cost policy.

Slice 10 deliberately does not implement adapter installation, activation, training, filesystem mutation, subprocess execution, model download, or remote inference.

## Reserved adapter routing namespace

Adapter-derived runtime model IDs use the exact namespace:

`aetheris-adapter-runtime://sha256/<artifact-sha256>`

`AdapterRuntimeRegistration.modelIdFor(...)` derives this ID from the verified `AdapterArtifactIdentity`. A plain `SyntraModelRuntime` is not allowed to publish a candidate in this namespace through `models()`.

This prevents an ordinary runtime catalog entry from being mistaken for a verified adapter registration.

## Verified registration contract

`AdapterRuntimeRegistration` requires:

- the exact Slice 9 `AdapterArtifactIdentity`;
- an `AdapterLifecycleResult` whose status is `ACTIVATED_VERIFIED`;
- an attempted and execution-verified effect;
- active state observed true;
- Stage 33 authority granted;
- lifecycle identity exactly equal to registration identity;
- a local runtime candidate;
- zero estimated runtime cost; and
- a candidate model ID exactly bound to the artifact SHA-256.

A rolled-back, blocked, failed, unverified, identity-drifted, remote, paid, or differently named candidate cannot create this registration.

## Runtime integration boundary

`AdapterAwareSyntraModelRuntime` is an optional extension of the existing `SyntraModelRuntime`. Existing runtimes remain source-compatible and behavior-compatible.

During catalog assembly, `SyntraLocalInferenceOrchestrator`:

1. builds the ordinary runtime catalog exactly as before;
2. rejects any ordinary candidate attempting to use the reserved adapter namespace;
3. reads adapter registrations only from runtimes implementing `AdapterAwareSyntraModelRuntime`;
4. checks that the registration provider matches the runtime provider;
5. requires the adapter's declared base model to exist as a local ordinary model on the same runtime; and
6. adds the verified adapter candidate to the same candidate list consumed by the existing `SyntraModelRouter`.

The router's ranking algorithm is not modified. Hardware bounds, capability matching, locality, health, streaming, zero-cost routing, and execution-target selection remain the router's responsibility.

## Inference continuity

If an adapter-aware runtime truthfully exposes a verified registration and the unchanged router selects it, the existing local inference path still:

- creates a typed `ModelInvocation`;
- preserves `ContextPack` evidence addresses;
- enforces contiguous streaming sequence numbers;
- preserves cancellation and partial-output truth;
- rejects remote execution; and
- emits existing evaluation hooks.

The runtime receives the adapter-bound model ID in the normal invocation contract. Slice 10 provides no concrete owner-PC adapter runtime implementation, so hosted CI proves only the registration and routing contract using a fake runtime.

## Rollback continuity

A `ROLLED_BACK_VERIFIED` lifecycle result cannot construct an `AdapterRuntimeRegistration`. A runtime must stop returning the registration after verified detach. This keeps a detached adapter from remaining eligible through the reserved registration path.

A future concrete owner-PC runtime integration must couple its live registration list to its own verified local activation state and preserve Slice 9 point-of-effect identity checks.

## Security and authority boundary

Slice 10 creates no new consequential-action authority. It does not call tools, connectors, browser/computer-use surfaces, credentials, financial systems, or external networks.

Model selection remains inference-only. Stage 32 emergency state and Stage 33 owner approval remain authoritative for activation/detach effects in Slice 9; Slice 10 cannot mint or bypass those approvals.

## Truth boundary

This slice does **not** claim that:

- an adapter is physically installed on the owner's PC;
- a concrete Ollama or other runtime currently implements adapter registrations;
- LoRA/QLoRA training has run;
- hosted CI proves owner-PC activation or inference behavior;
- owner-PC GPU, VRAM, RAM, drivers, CUDA, TTFT, tokens/sec, thermals, semantic quality, or failure rate have been measured; or
- registration grants tool or consequential-action authority.

## Acceptance evidence

`Phase13AdapterAwareRuntimeRegistrationTest` proves that:

1. an exact verified-active adapter can enter the routing catalog and stream through the unchanged inference path;
2. rolled-back and unverified lifecycle results cannot register;
3. activation identity and artifact-bound model identity must match exactly;
4. a plain runtime cannot inject the reserved adapter namespace;
5. the declared base model must exist on the same local provider; and
6. registered adapter candidates remain local and zero-cost.

## Next dependency

After the exact Slice 10 PR head and canonical merge SHA are fully certified, the next slice may implement a concrete owner-PC adapter runtime bridge or stronger live registration-state reconciliation. Physical activation/performance claims remain prohibited until measured on the real target hardware/runtime.
