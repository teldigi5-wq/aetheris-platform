# Stage 22 — Release Hardening & Merge Readiness

Stage 22 is a repository-only hardening milestone. It deliberately does **not** advance the Stage 21 physical activation path while the owner's target Windows PC is unavailable.

## Goal

Make the long-running Syntra/Aetheris branch easier to review, reproduce and eventually merge without converting repository evidence into false hardware or production claims.

## Implemented controls

1. **Least-privilege CI** — the build workflow declares `contents: read` and keeps build/test jobs separated from the new release-hardening job.
2. **High-confidence secret gate** — `scripts/stage22/release_gate.py` scans tracked files for private-key material, common provider-token formats, literal long secret assignments and dangerous tracked credential-file names. It records only file/rule metadata; matched secret values are never written to artifacts.
3. **Persistence schema guard** — `scripts/stage22/schema_guard.py` detects duplicate explicit JPA table names and unsafe production `ddl-auto` modes in main resources.
4. **Deterministic source SBOM** — `scripts/stage22/generate_sbom.py` inventories Maven, Python and Node declarations into deterministic CycloneDX 1.5 JSON. Missing dependency lockfiles are evidence warnings rather than silently ignored facts.
5. **Deterministic release manifest** — `scripts/stage22/release_manifest.py` records the exact commit/tree plus SHA-256 and size for every tracked file.
6. **Reproducibility check** — CI regenerates the SBOM and release manifest twice and byte-compares them.
7. **CODEOWNERS** — critical release, workflow, workstation-agent and staged-control-plane paths are assigned to the repository owner. This becomes enforceable only after branch protection/rulesets are enabled.
8. **Merge-readiness evidence** — `scripts/stage22/merge_readiness.py` calculates a deterministic repository hardening score and emits explicit blockers rather than auto-merging.
9. **Evidence artifact** — GitHub Actions uploads the Stage 22 JSON evidence bundle for review for 14 days.

## Expected current blockers

The repository can pass all critical Stage 22 security/schema/determinism gates while still reporting `HARDENED_AWAITING_EXTERNAL_MERGE_GATES`.

Two expected blockers are intentionally visible:

- `dashboard/package-lock.json` does not currently exist, so frontend transitive dependency resolution is not fully reproducible.
- `main` branch protection / required status checks are a GitHub repository setting and must be enabled and verified externally before merge.

Neither blocker is papered over by a synthetic attestation.

## Relationship to Stage 21

Stage 21 remains `BLOCKED_PENDING_HARDWARE`. Stage 22 cannot set `physicalPilotComplete`, cannot grant an activation lease, cannot mutate the target, and cannot turn CI evidence into target evidence.

## Merge discipline

A green Stage 22 job means the repository hardening scripts ran successfully. It does **not** mean PR #7 should be merged automatically. Owner review, branch protection, status-check requirements, dependency-locking decisions and the previously documented hardware boundary remain separate decisions.
