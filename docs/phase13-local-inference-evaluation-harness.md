# Phase 13 Slice 6 — Local Inference Evaluation Harness

## Certified baseline

Canonical development baseline: `301ec5adccb9360e3ebbea0608137e8fc61b1487`.

Before this slice was opened, the final Slice 5 PR head `7465d997f9a63574ab5593fd72b9b9435548cecc` was observed with all pull-request workflows successful, and the canonical Slice 5 merge SHA was observed with **36/36 push-triggered workflows completed successfully**.

## Objective

Add a repository-safe evaluation layer around the Slice 5 local inference outcomes without fabricating semantic quality, owner-PC performance, or physical hardware evidence.

The evaluation path is:

`LocalInferenceResult -> InferenceEvaluationCase -> SyntraInferenceEvaluationHarness -> InferenceEvaluationAssessment`

Measured model observations may additionally flow through the existing Stage 31 ranking engine:

`ObservedModelEvaluation -> ModelBenchmarkEngine -> EvaluatedModelScore`

## Structural evaluation cases

`InferenceEvaluationCase` declares only observable expectations:

- exact inference task;
- exact expected status;
- exact ordered evidence-address continuity;
- optional expected provider/model identity; and
- whether a non-blank output is required.

`SyntraInferenceEvaluationHarness` reports explicit failure codes such as task, status, citation, output, provider, and model mismatches. It does not turn these structural checks into a claim that the generated answer is correct, safe, useful, or citation-faithful.

The harness can be bound to Slice 5 through `LocalInferenceEvaluationHook` with `hookFor(...)`, preserving the existing observation-only extension point. The hook receives no tool authority, approval capability, connector credential, filesystem executor, shell executor, or network client.

## Measured model evidence and Stage 31 reuse

This slice deliberately does not create a second model-ranking algorithm.

`ObservedModelEvaluation` validates the same quality, first-token latency, throughput, failure-rate, VRAM, cost, and local/private fields already consumed by Stage 31 `ModelBenchmarkSample`. `SyntraInferenceEvaluationHarness.rankObservedModels(...)` delegates ranking to the existing `ModelBenchmarkEngine` and then reattaches the evidence provenance to each score.

Duplicate model IDs fail closed because Stage 31 scores use model ID as the durable ranking identity.

## Evidence provenance

Every observed model sample is explicitly classified as one of:

- `HOSTED_SYNTHETIC` — repository/CI or otherwise synthetic evidence that must not be represented as owner-PC measurement; or
- `OWNER_HARDWARE` — measurements actually collected on the owner's target hardware.

`EvaluatedModelScore.ownerHardwareEvidence()` is therefore provenance, not an inference from a model name, GPU target profile, CI runner, or configured VRAM value.

This repository slice itself adds **no new `OWNER_HARDWARE` measurement evidence**. Tests use typed example values only to prove provenance handling and deterministic filtering/ranking.

## Zero-cost and private ranking boundary

Model ranking continues to use the existing Stage 31 hard filters. When zero-cost and private-only modes are requested, paid or non-local samples are excluded by `ModelBenchmarkEngine`; Slice 6 does not weaken or fork those rules.

## Security and authority boundary

Evaluation is observation only.

- It cannot execute tools or connectors.
- It cannot approve actions.
- It cannot alter owner rules or Phase 12 execution authority.
- It cannot turn model text into an execution command.
- It adds no HTTP client or remote inference path.
- It does not persist secrets, prompts, or credentials.

Consequential execution remains behind the existing Stage 33 lifecycle and Phase 12 authority/approval/audit boundaries.

## Acceptance evidence

`Phase13LocalInferenceEvaluationHarnessTest` proves that:

1. typed completed outcomes can be assessed through the Slice 5 evaluation hook;
2. task, status, citation, output, provider, and model drift are reported explicitly;
3. unavailable inference remains an evaluatable explicit outcome without fabricated runtime output;
4. measured ranking delegates to Stage 31 and preserves evidence provenance;
5. zero-cost/private ranking excludes paid and remote observations;
6. hosted synthetic evidence cannot report itself as owner-hardware evidence;
7. duplicate model IDs fail closed before ranking; and
8. metric ranges reuse Stage 31 validation rather than accepting invalid fabricated values.

## Truth boundary

This slice does **not** claim that:

- Ollama or any model weights are installed on the owner's PC;
- an RTX 4050, CUDA runtime, VRAM amount, RAM amount, thermals, power draw, TTFT, tokens/sec, or failure rate has been physically observed on the owner PC;
- hosted CI timings represent owner hardware;
- a structural pass means the answer is factually correct or semantically high quality;
- citation presence proves the model actually relied on every cited item;
- prompt injection is solved;
- tokenizer-exact context accounting exists; or
- model output has tool or policy authority.

## Next dependency

After the exact Slice 6 PR head is fully green and its canonical merge SHA is certified, continue Phase 13 with the next owner-controlled model-development slice. The prior progression places optional lawful LoRA/QLoRA experimentation before the evaluated orchestration/policy-model stage, but physical training and owner-hardware benchmark claims remain blocked until the actual target machine is available. Repository work should first preserve data-rights, secret-scrubbing, provenance, rollback, and opt-in boundaries rather than claiming a trained model exists.
