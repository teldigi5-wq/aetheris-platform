# Phase 13 Slice 8 — Candidate-Adapter Evaluation and Promotion Governance

## Certified baseline

Canonical development baseline: `6810513eb2fd38b31f8639b589ebdc2a16c4353d`.

That Phase 13 Slice 7 merge was independently observed with **38/38 push-triggered workflows successful** before this slice was completed.

## Objective

Turn candidate-adapter evaluation evidence into a deterministic, fail-closed promotion decision without adding training execution, artifact installation, model loading, network access, tool authority, or base-model mutation.

Slice 8 is a governance layer. It evaluates supplied evidence against an explicit policy and records whether a candidate is rejected, held for owner approval, or approved by policy for local-use promotion. It does not itself make adapter weights exist, install them, register them with Ollama, or mutate the active model router.

## Inputs

The decision path is explicit:

`AdaptationExperimentPlan + AdapterPromotionEvidence + AdapterPromotionPolicy + owner approval -> AdapterPromotionDecision`

The Slice 7 `AdaptationExperimentPlan` remains the identity and safety anchor. Slice 8 requires exact continuity for:

- experiment ID;
- candidate adapter address;
- base model ID;
- dataset SHA-256;
- local-only planning;
- immutable base-model weights;
- `executionAuthorized=false`; and
- `DETACH_ADAPTER` rollback.

Any identity drift fails closed before promotion policy is evaluated.

## Candidate and baseline evidence

`AdapterPromotionEvidence` binds one baseline observation and one candidate observation to the same adaptation plan plus one or more structural inference assessments.

The baseline observation must identify the exact planned base model. The candidate observation and every structural assessment route must identify the exact planned `aetheris-adapter://candidate/...` address.

The evidence uses the existing Phase 13 `ObservedModelEvaluation` contract, which in turn validates its numeric sample through the existing Stage 31 `ModelBenchmarkSample` contract. Slice 8 does not introduce a competing benchmark data model or ranking algorithm.

The default policy requires both baseline and candidate observations to be marked `OWNER_HARDWARE`. This is provenance carried by the supplied evidence; the repository does **not** independently attest the physical machine, GPU, VRAM, CUDA stack, adapter artifact, or measurement process.

## Default promotion policy

`AdapterPromotionPolicy.conservativeDefault()` requires:

- at least `0.02` absolute improvement in the supplied benchmark quality score;
- no increase in supplied failure rate;
- candidate first-token latency no worse than `1.10x` the baseline;
- candidate throughput at least `0.90x` the baseline; and
- owner-hardware evidence provenance for both baseline and candidate.

Every structural inference assessment must also pass.

These thresholds are deterministic governance defaults, not scientific claims that they are universally optimal for every model, task, GPU, or workload.

## Decision states

### `REJECTED`

A candidate is rejected when any policy or structural blocker exists. Owner approval cannot override a failed policy check.

Possible blockers include:

- `PRIVATE_LOCAL_EVIDENCE_REQUIRED`;
- `OWNER_HARDWARE_EVIDENCE_REQUIRED`;
- `STRUCTURAL_EVALUATION_FAILED:<case-id>`;
- `QUALITY_IMPROVEMENT_BELOW_THRESHOLD`;
- `FAILURE_RATE_REGRESSION`;
- `FIRST_TOKEN_LATENCY_REGRESSION`; and
- `THROUGHPUT_REGRESSION`.

### `HELD`

A candidate that passes the evidence policy but lacks explicit owner approval is held with `OWNER_APPROVAL_REQUIRED`.

A held candidate has no promoted adapter address and is not routing-eligible.

### `APPROVED_FOR_LOCAL_USE`

A candidate reaches this status only when all policy checks pass and explicit owner approval is supplied to the governance decision.

The decision derives an immutable logical promoted address by replacing the candidate namespace with `aetheris-adapter://promoted/...`, preserves `localOnly=true`, preserves immutable base weights, and preserves `DETACH_ADAPTER` rollback.

`routingEligible=true` in this record is a **governance eligibility result only**. Slice 8 does not install, load, register, persist, activate, or route to an adapter. A future activation layer must independently prove that the approved artifact exists, matches the evaluated identity, remains local, and is authorized at the actual execution boundary before the router can use it.

## Owner-approval boundary

The `ownerApproval` input is an explicit precondition consumed by this pure in-memory governance service. The service does not mint, persist, or independently authenticate an owner approval, and it has no access to credentials or approval stores.

A future consequential activation path must obtain owner authorization from the established Phase 12 / Stage 33 authority boundary at the point of effect. Passing `true` to this non-executing decision function is not permission to bypass those controls.

## Security boundary

Slice 8 deliberately remains non-executing and in-memory. The implementation adds no:

- training subprocess or shell command;
- filesystem adapter writer;
- model download or package installation;
- HTTP or remote inference client;
- connector/tool/browser/computer-use authority;
- credential or secret-store access;
- production routing mutation;
- base-model weight mutation; or
- automatic rollback execution.

Owner approval cannot override failed evaluation evidence, identity mismatches, non-private evidence, or immutable-base-model safety invariants.

## Truth boundary

This slice does **not** claim that:

- LoRA or QLoRA training has run;
- candidate or promoted adapter weights exist on disk;
- an adapter has been loaded by Ollama or another local runtime;
- the owner's GPU, VRAM, RAM, CUDA stack, or driver has been physically observed by this repository;
- the sample benchmark numbers used in tests were measured on owner hardware;
- a supplied `OWNER_HARDWARE` provenance label is cryptographic hardware attestation;
- the benchmark `qualityScore` proves semantic correctness, factuality, citation faithfulness, or safety;
- hosted CI performance represents owner-PC performance;
- promotion eligibility means runtime activation has occurred; or
- model output gains tool, connector, approval, or execution authority.

The focused tests use typed synthetic values to prove governance behavior and provenance handling only.

## Acceptance evidence

`Phase13AdapterPromotionGovernanceTest` proves that:

1. a qualifying candidate with matching owner-hardware evidence and explicit owner approval can receive an approved local-use governance decision;
2. a qualifying candidate remains held without explicit owner approval;
3. measurable regressions reject a candidate even when owner approval is supplied;
4. synthetic evidence and failed structural assessments are rejected by the conservative default policy;
5. plan/evaluation identity drift fails closed;
6. structural assessments must observe the exact candidate adapter identity; and
7. the decision contract cannot mark a held candidate as routing-eligible.

The dedicated workflow additionally verifies that the Slice 8 surface exists, uses the expected owner/hardware/rollback guards, and does not add process execution, network access, filesystem writes, or tool/connector/approval-package coupling.

## Next dependency

After the exact Slice 8 PR head is fully green and the canonical merge SHA is independently certified, the next Phase 13 slice should address **approved-adapter artifact identity and local activation/rollback governance**.

That future slice must keep activation separate from evaluation, revalidate the exact evaluated adapter identity at the point of effect, preserve explicit owner authority, and avoid claiming physical owner-PC validation until real owner-hardware evidence is collected.
