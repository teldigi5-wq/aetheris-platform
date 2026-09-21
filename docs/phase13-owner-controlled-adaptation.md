# Phase 13 Slice 7 — Owner-Controlled Adaptation Planning

## Certified baseline

Canonical development baseline: `f3dcf8254d54a4d230459c4a1c7df31ef7dfacd9`.

Before this slice was opened, the Slice 6 canonical merge SHA was observed with **37/37 push-triggered workflows completed successfully**.

## Objective

Define the safe repository contracts needed for an optional future LoRA/QLoRA experiment without claiming that training has run, model weights exist, or owner hardware has been measured.

Slice 7 is deliberately a **dataset-preparation and planning boundary only**:

`AdaptationSourceDocument -> AdaptationDatasetBuilder -> AdaptationDatasetManifest -> SyntraAdaptationPlanningService -> AdaptationExperimentPlan`

There is no training runner in this slice.

## Explicit rights and provenance

Every source must declare:

- a stable source ID;
- one supported rights basis: `OWNER_AUTHORED`, `EXPLICIT_PERMISSION`, `COMPATIBLE_LICENSE`, or `PUBLIC_DOMAIN`;
- a non-blank rights reference; and
- a non-blank provenance reference.

Unknown or implicit rights are not accepted by the typed contract. The runtime does not attempt to infer copyright permission from content text, repository location, model output, or metadata labels.

## Protected and superseded data

`AdaptationDatasetBuilder` rejects:

- every source marked `protectedData=true`; and
- every source tagged with the existing Stage 9 `IncrementalKnowledgeIngestionService.TOMBSTONE_TAG`.

This reuses the established memory lifecycle boundary instead of creating a parallel tombstone vocabulary.

## Secret handling

The Slice 7 scrubber provides a bounded defense-in-depth pass for common credential-shaped material before content enters an adaptation manifest.

It redacts recognized bearer tokens, GitHub-token shapes, AWS access-key shapes, and common labeled secret assignments. Private-key blocks fail closed instead of being redacted into a trainable document.

The scrubber records the exact redaction count and preserves both the original source-content hash and the sanitized-content hash.

This is **not** a claim of perfect secret detection. Regex-based scanning cannot prove that arbitrary text is secret-free. Future physical training remains responsible for additional owner review and any stronger secret/DLP tooling available on the target machine.

## Deterministic manifest

Dataset entries are sorted by source ID. The manifest SHA-256 binds:

- manifest version;
- source identity;
- original source-content hash;
- sanitized-content hash;
- rights basis;
- rights reference;
- provenance reference; and
- redaction count.

Input ordering therefore does not change the dataset identity, while meaningful source, provenance, rights, or sanitized-content changes do.

No timestamp is included in the deterministic dataset hash.

## Owner opt-in and rollback

`SyntraAdaptationPlanningService` requires explicit `ownerOptIn=true` before it will create an experiment plan.

Every Slice 7 plan is hard-bound to:

- local-only planning;
- immutable base-model weights;
- `executionAuthorized=false`;
- candidate adapter identity under `aetheris-adapter://candidate/...`; and
- `DETACH_ADAPTER` rollback.

A candidate address is a planned identity only. It does not prove that an adapter file exists.

## Security and authority boundary

Slice 7 adds no:

- model download;
- training subprocess;
- shell or terminal invocation;
- Python execution bridge;
- HTTP/network client;
- filesystem artifact writer;
- connector or tool authority;
- approval bypass; or
- automatic base-model modification.

Consequential execution remains governed by the existing Phase 12 and Stage 33 boundaries. Model adaptation cannot grant runtime authority.

## Acceptance evidence

`Phase13OwnerControlledAdaptationTest` proves that:

1. manifest identity is deterministic across source ordering;
2. rights and provenance survive into dataset entries;
3. supported secret-like values are scrubbed with recorded redaction evidence;
4. private-key material fails closed;
5. protected data is rejected;
6. Stage 9 tombstoned data is rejected;
7. duplicate source identity fails closed;
8. rights/provenance declarations are mandatory; and
9. owner opt-in is mandatory while created plans remain local-only, base-weight immutable, non-executing, and detach-adapter rollbackable.

## Truth boundary

This slice does **not** claim that:

- LoRA or QLoRA training has executed;
- a training framework such as PEFT, Transformers, bitsandbytes, Unsloth, Axolotl, or CUDA is installed;
- any adapter weights exist;
- any training dataset has been exported to disk;
- any owner-PC GPU, VRAM, RAM, thermals, power, throughput, loss curve, or training duration has been measured;
- regex scrubbing proves a dataset contains no secrets;
- declared rights have been independently legally adjudicated; or
- an adapted model is safer or more capable than its base model.

## Next dependency

After the exact Slice 7 PR head is fully green and the canonical merge SHA is certified, the next Phase 13 repository slice should govern **candidate-adapter evaluation and promotion**: an adapter must remain detached by default, be compared against the base model through measured evaluation evidence, preserve provenance, require explicit owner promotion, and support immediate rollback. Physical adapter training and owner-hardware performance claims remain blocked until they actually occur on the target machine.
