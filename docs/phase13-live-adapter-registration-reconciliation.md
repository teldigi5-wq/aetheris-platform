# Phase 13 Slice 11 — Live Adapter Registration-State Reconciliation

## Certified baseline

Canonical development baseline: `11c1067826e4f4a9346e6c85b535e5d46cb60787`.

Before Slice 11 was created, the canonical Slice 10 merge SHA was observed with **41/41 push-triggered workflows successful**, including CodeQL and two-pass reproducibility, with zero failed, cancelled, queued, or in-progress workflows.

## Objective

Close the stale-registration gap between Slice 10's immutable verified activation registration and the current local runtime state.

A Slice 9 `ACTIVATED_VERIFIED` result remains necessary, but it is no longer sufficient by itself for routing. Every adapter registration must also have a current read-only runtime observation that says the exact artifact still exists and is active.

The Slice 11 catalog path is:

`ACTIVATED_VERIFIED registration -> structural provider/base-model checks -> fresh read-only observation -> exact identity + exists + active -> unchanged SyntraModelRouter`

## Compatibility-safe observation contract

`AdapterAwareSyntraModelRuntime` gains:

`Optional<AdapterArtifactObservation> observeAdapter(AdapterArtifactIdentity identity)`

The method has a default `Optional.empty()` implementation. Existing runtime implementations therefore remain source-compatible, but they fail closed for adapter routing until they implement truthful current-state observation.

The observation method is read-only by contract. It is not activation authority and must not install, activate, detach, download, train, mutate files, launch subprocesses, or call remote services.

## Reconciliation semantics

`SyntraLocalInferenceOrchestrator` still builds ordinary runtime candidates exactly as before. For every Slice 10 adapter registration it now:

1. preserves all Slice 10 registration validation;
2. requires the declared base model on the same local provider;
3. calls `observeAdapter(...)` during each inference catalog assembly;
4. excludes the adapter when no observation is available;
5. excludes the adapter when the observed identity differs from the registered identity;
6. excludes the adapter when `exists=false`; and
7. excludes the adapter when `active=false`.

Excluded adapters never reach `SyntraModelRouter`. The unchanged router can therefore select another valid ordinary model or return unavailable.

There is no positive cache in Slice 11. A runtime that observes an adapter active for one inference and detached for the next must lose adapter route eligibility on that next catalog assembly.

## Authority continuity

Slice 11 creates no new execution authority. The observation gate cannot mint or replace Slice 9 activation authority.

Stage 32 emergency controls and Stage 33 scoped owner approval remain authoritative for activation/detach effects. Slice 11 calls neither activation nor detach and does not consume approval tokens.

A current `active=true` observation without the immutable Slice 10 `ACTIVATED_VERIFIED` registration is not enough to create a routing candidate.

## Inference continuity

When the exact registered artifact is still observed active, the existing inference path remains unchanged:

- the existing router chooses among eligible candidates;
- context citations remain preserved in `ModelInvocation` and `LocalInferenceResult`;
- remote execution remains rejected;
- streaming sequence continuity remains enforced;
- cancellation semantics remain intact; and
- evaluation hooks remain observation-only.

## Truth boundary

`AdapterArtifactObservation` is runtime-reported evidence, not cryptographic attestation. Slice 11's hosted tests use an in-memory fake runtime and prove only reconciliation behavior.

This slice does **not** claim that:

- an adapter is physically installed or active on the owner's PC;
- Ollama or another concrete owner-PC runtime implements this observation contract yet;
- a live observation is hardware-backed or tamper-proof;
- LoRA/QLoRA training has run;
- hosted CI proves owner-PC runtime behavior; or
- owner-PC GPU, VRAM, RAM, driver, CUDA, TTFT, tokens/sec, thermals, semantic quality, or failure rate have been measured.

## Acceptance evidence

`Phase13LiveAdapterRegistrationReconciliationTest` proves that:

1. an exact current active observation preserves verified adapter routing and evidence continuity;
2. a registration is re-observed on every inference and verified detach removes eligibility on the next inference;
3. missing artifacts and identity drift are excluded before routing/invocation; and
4. the default observation contract is source-compatible but fail-closed.

The strengthened Slice 10 test also supplies a truthful active observation, proving the original verified-registration route remains valid under Slice 11.

## Next dependency

After exact-head and canonical certification, the next slice may strengthen point-of-invocation freshness/lease semantics or implement a concrete owner-PC adapter runtime bridge when real target-runtime evidence is available. No physical activation or performance claim is permitted without direct owner-PC measurement.
