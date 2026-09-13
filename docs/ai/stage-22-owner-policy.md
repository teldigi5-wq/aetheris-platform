# Stage 22 Owner Policy

Stage 22 improves repository release discipline; it does not change the owner's authority model.

## Non-negotiable rules

- PR #7 remains draft/unmerged until the owner deliberately changes that state.
- `main` must not be force-updated by Stage 22 automation.
- A CI pass is not permission to merge.
- CODEOWNERS does not count as enforced review until GitHub branch protection/rulesets require it.
- Missing lockfiles and external repository-setting gaps must be reported as blockers, not fabricated as passing evidence.
- Secret scanning must never print or persist matched secret values.
- No signing private key is committed to the repository.
- Stage 21 remains `BLOCKED_PENDING_HARDWARE` until genuine target measurements exist.
- No unrestricted shell, UAC/admin/security bypass or credential export is introduced.
- Live-money orders, withdrawals and transfers remain disabled/outside AI authority.

## Current release posture

The intended Stage 22 source-level status is `HARDENED_AWAITING_EXTERNAL_MERGE_GATES` until the dashboard dependency graph is locked and `main` protection/required checks are verified. `mergeAllowed` and `autoMergeAllowed` remain false in the generated readiness evidence.
