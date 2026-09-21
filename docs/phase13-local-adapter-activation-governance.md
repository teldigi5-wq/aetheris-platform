# Phase 13 Slice 9 — Approved-Adapter Identity and Local Activation / Rollback Governance

## Certified baseline

Canonical development baseline: `df35a3d8aecba1721532d724d19d496c4deac7e5`.

Before Slice 9 was created, the canonical Phase 13 Slice 8 merge SHA was independently observed with **39/39 push-triggered workflows successful**, zero failures, and zero workflows still in progress.

## Objective

Carry an adapter from Slice 8's **governance eligibility** state to a point-of-effect local activation or detach attempt without weakening Phase 12 / Stage 33 authority, inventing an adapter artifact, or claiming an unverified runtime effect.

The Slice 9 path is:

`Slice 7 plan + Slice 8 evaluation/promotion + immutable artifact identity -> local inspection -> Stage 33 preflight/owner approval -> local activation port -> Stage 33 execution verification -> typed lifecycle result`

Slice 9 does not mutate `SyntraModelRouter` and does not provide a concrete Ollama, filesystem, subprocess, installer, or model-download implementation.

## Immutable artifact identity

`AdapterArtifactIdentity` binds the point-of-effect artifact to:

- experiment ID;
- candidate `aetheris-adapter://candidate/...` address;
- promoted `aetheris-adapter://promoted/...` address;
- base model ID;
- dataset SHA-256;
- adapter artifact SHA-256;
- logical `aetheris-adapter-artifact://sha256/<digest>` address;
- `localOnly=true`; and
- immutable base-model weights.

The artifact SHA-256 is an expected identity supplied to this governance layer. The repository does not claim that the corresponding bytes exist until a concrete local activation port observes them.

## Promotion revalidation

`SyntraAdapterLifecycleGovernanceService` does not trust a supplied promotion decision by itself. Before inspecting or activating an artifact it recomputes the Slice 8 promotion decision from the original `AdaptationExperimentPlan`, `AdapterPromotionEvidence`, and `AdapterPromotionPolicy` with explicit promotion approval.

The supplied decision must exactly equal that recomputed decision and must remain:

- `APPROVED_FOR_LOCAL_USE`;
- owner-approved at the Slice 8 governance layer;
- local-only;
- routing-eligible as a governance result;
- free of blockers;
- immutable with respect to base weights; and
- bound to `DETACH_ADAPTER` rollback.

The artifact identity must then exactly match the approved experiment, candidate address, promoted address, base model, and dataset hash.

## Point-of-effect inspection

Before Stage 33 owner authority is consumed, the service calls `LocalAdapterActivationPort.inspect(...)`.

Activation fails closed when the observation is absent, the artifact does not exist, the identity differs, or the adapter is already active. Rollback fails closed when the same exact artifact is not observed active.

This keeps malformed or missing artifacts from consuming owner approval or reaching an effect method.

## Stage 33 authority bridge

Stage 33 intentionally keeps `GovernanceAction`, `ApprovalGrant`, and related records package-private. Slice 9 therefore adds the narrow public `AdapterActivationAuthorityBridge` inside the existing Stage 33 package instead of duplicating approval logic in `syntracore`.

The bridge maps adapter activation and detach to existing Stage 33 governance as private, local, zero-cost, privileged side effects with HIGH declared risk. As a result, the established engine requires:

- successful preview/simulation evidence;
- exact action ID and scope-bound owner approval;
- approval time validity;
- one-time token replay protection when requested;
- Stage 32 emergency-state clearance; and
- post-effect verification.

Activation and rollback use distinct Stage 33 action IDs. An approval scoped to activation cannot silently authorize detach.

The activation scope is the exact promoted adapter address.

## Execution truth

After an authorized port call, Slice 9 feeds the observation through the existing `GovernanceVerificationService`.

The lifecycle result preserves the difference between:

- `ACTIVATED_VERIFIED`;
- `ACTIVATION_FAILED_VERIFIED`;
- `ACTIVATION_UNVERIFIED`;
- `ROLLED_BACK_VERIFIED`;
- `ROLLBACK_FAILED_VERIFIED`;
- `ROLLBACK_UNVERIFIED`; and
- `BLOCKED`.

A method return is not enough to claim success. Verified success requires an exact post-effect artifact identity, the expected active/detached state, a verifier identity, and an evidence reference.

If the port throws or the post-effect identity drifts, the outcome remains explicitly unverified rather than being rewritten as success.

## Local activation port boundary

`LocalAdapterActivationPort` is an interface only. It defines `inspect`, `activate`, and `detach` observations for a future concrete local runtime integration.

Slice 9 intentionally does not implement those effects using HTTP, shell/process execution, filesystem mutation, Ollama commands, model installation, or remote services. The focused tests use an in-memory fake solely to prove governance ordering and truth-state handling.

A future runtime slice must implement the port under the same identity, local-only, authority, and verification rules before any claim of real adapter activation can be made.

## Security boundary

Slice 9 does not grant model output authority. The model router remains unchanged. Tool, connector, browser, computer-use, credential, financial, and external network authority remain outside this slice.

Stage 32 emergency control continues to outrank adapter activation. Stage 33 approval replay protection and exact scope matching remain authoritative.

Slice 8's boolean promotion approval is not reused as point-of-effect execution permission. Stage 33 approval is independently required at activation and rollback.

## Truth boundary

This slice does **not** claim that:

- a candidate or promoted adapter file currently exists on the owner's PC;
- LoRA or QLoRA training has actually run;
- Ollama or another runtime supports this activation port yet;
- an adapter was physically loaded, registered, or detached on owner hardware;
- owner-PC GPU, VRAM, RAM, driver, CUDA, TTFT, tokens/sec, thermals, or failure rate were measured by this slice;
- an `OWNER_HARDWARE` evidence label is cryptographic attestation;
- hosted CI proves owner-PC runtime behavior;
- promotion eligibility means routing activation; or
- adapter activation grants tool or consequential-action authority.

The lifecycle tests prove the governance and verification contract around a fake local port, not physical hardware execution.

## Acceptance evidence

`Phase13AdapterActivationGovernanceTest` proves that:

1. an exact approved artifact can reach a verified activation only after Stage 33 scoped owner authority;
2. missing point-of-effect approval blocks before the activation method;
3. artifact identity drift blocks before owner authority or effect;
4. a forged/held promotion decision cannot reach the activation port;
5. rollback requires its own action-scoped approval and verifies `DETACH_ADAPTER` state;
6. one-time Stage 33 approval cannot be replayed;
7. post-effect identity drift remains explicitly unverified; and
8. Stage 32 emergency STOP blocks activation even when a structurally valid owner approval is supplied.

## Next dependency

After the exact Slice 9 PR head and canonical merge SHA are fully certified, the next Phase 13 slice may implement a **concrete owner-PC local adapter runtime integration** or **adapter-aware routing registration**, but only when it can preserve these point-of-effect identity and authority checks.

No owner-PC performance or physical activation claim should be made until the real target hardware/runtime path is executed and measured.
