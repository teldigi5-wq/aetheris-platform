# AI Runtime Certification Drift Audit

## Purpose

The platform integrates an exact certified checkpoint of `teldigi5-wq/aetheris-ai-runtime`. The runtime repository may continue to advance after that checkpoint, so commit-count difference alone is not treated as certification failure.

The audit implemented by `tools/check_ai_runtime_certification_drift.py` compares the platform-recorded certified SHA with live runtime `main` and classifies the changed paths semantically.

## States

| Status | Meaning | CI result |
|---|---|---|
| `ALIGNED` | runtime `main` equals the certified checkpoint | pass |
| `NON_RUNTIME_DRIFT` | only docs, licensing, dependency-governance or other non-runtime paths changed | pass |
| `EVIDENCE_PIPELINE_DRIFT` | non-authoritative tests, evidence-only workflows or support scripts changed without runtime source/config/control changes | pass, review recommended |
| `RUNTIME_PROMOTION_REQUIRED` | runtime-owned source, runtime configuration, or an authoritative build/certification control changed | fail |
| `CERTIFICATION_HISTORY_INVALID` | the certified checkpoint is no longer an ancestor of runtime `main` | fail |
| `AUDIT_ERROR` | the live comparison could not be evaluated reliably | fail |

Runtime-owned source roots come from `architecture/ai-runtime-certification-reference.json` and currently include:

- `orchestrator-service/`
- `workstation-agent/`
- `aetheris-reasoning/`
- `aetheris-quant/`

`architecture/` and `configs/` in the runtime repository are also promotion-sensitive because they may change runtime behavior or certification policy.

The classifier also treats a narrow set of authoritative build and certification controls as promotion-sensitive. These include:

- the baseline runtime build, dependency/reproducibility, image-provenance, migrated-certification and Stage 26 safety workflows;
- migrated Phase 9-13 and Stage 28-33 runtime-owned certification workflows;
- Stage 26, Stage 28 and Stage 29 runtime certification/logic scripts;
- the dependency-lockdown verifier;
- the extraction provenance record; and
- root Java/Maven toolchain controls such as `.java-version`, Maven wrapper configuration and wrapper launchers.

This distinction is deliberate. A CodeQL-only change, ordinary test-only change, documentation change or unrelated support-workflow change remains visible without automatically invalidating a source-equivalent runtime checkpoint. A change that can alter how the runtime is built, packaged, safety-certified or promoted fails closed and requires deliberate promotion evidence.

## CI

`.github/workflows/ai-runtime-certification-drift.yml` runs:

- daily on a schedule;
- manually through `workflow_dispatch`;
- when the certification reference, classifier, classifier tests or workflow itself changes on `main` or the canonical development line.

The job uploads `build/runtime-certification/drift-report.json` as evidence.

This workflow is intentionally separate from the stable-main required-check set. A later runtime source or authoritative certification-control change should raise a visible certification failure without making unrelated platform commits depend on an external repository's transient availability.

## Promotion rule

A newer runtime SHA must not replace the certified checkpoint merely to make repository tips equal. When the audit reports `RUNTIME_PROMOTION_REQUIRED`, promotion requires the applicable runtime suite plus platform integration evidence to pass before `architecture/ai-runtime-certification-reference.json` is deliberately advanced.

## Truth boundary

This audit is repository evidence only. It does not change `BLOCKED_PENDING_HARDWARE`, and it does not claim physical-PC certification, production activation, registry publication or live-money execution.
