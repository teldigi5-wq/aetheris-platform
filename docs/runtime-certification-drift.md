# AI Runtime Certification Drift Audit

## Purpose

The platform integrates an exact certified checkpoint of `teldigi5-wq/aetheris-ai-runtime`. The runtime repository may continue to advance after that checkpoint, so commit-count difference alone is not treated as certification failure.

The audit implemented by `tools/check_ai_runtime_certification_drift.py` compares the platform-recorded certified SHA with live runtime `main` and classifies the changed paths semantically.

## States

| Status | Meaning | CI result |
|---|---|---|
| `ALIGNED` | runtime `main` equals the certified checkpoint | pass |
| `NON_RUNTIME_DRIFT` | only docs, licensing, dependency-governance or other non-runtime paths changed | pass |
| `EVIDENCE_PIPELINE_DRIFT` | runtime tests, scripts or GitHub Actions changed without runtime source/config changes | pass, review recommended |
| `RUNTIME_PROMOTION_REQUIRED` | runtime-owned source or runtime configuration changed | fail |
| `CERTIFICATION_HISTORY_INVALID` | the certified checkpoint is no longer an ancestor of runtime `main` | fail |
| `AUDIT_ERROR` | the live comparison could not be evaluated reliably | fail |

Runtime-owned source roots come from `architecture/ai-runtime-certification-reference.json` and currently include:

- `orchestrator-service/`
- `workstation-agent/`
- `aetheris-reasoning/`
- `aetheris-quant/`

`architecture/` and `configs/` in the runtime repository are also promotion-sensitive because they may change runtime behavior or certification policy.

## CI

`.github/workflows/ai-runtime-certification-drift.yml` runs:

- daily on a schedule;
- manually through `workflow_dispatch`;
- when the certification reference, classifier, classifier tests or workflow itself changes on `main` or the canonical development line.

The job uploads `build/runtime-certification/drift-report.json` as evidence.

This workflow is intentionally separate from the stable-main required-check set. A later runtime source change should raise a visible certification failure without making unrelated platform commits depend on an external repository's transient availability.

## Promotion rule

A newer runtime SHA must not replace the certified checkpoint merely to make repository tips equal. When the audit reports `RUNTIME_PROMOTION_REQUIRED`, promotion requires the applicable runtime suite plus platform integration evidence to pass before `architecture/ai-runtime-certification-reference.json` is deliberately advanced.

## Truth boundary

This audit is repository evidence only. It does not change `BLOCKED_PENDING_HARDWARE`, and it does not claim physical-PC certification, production activation, registry publication or live-money execution.
