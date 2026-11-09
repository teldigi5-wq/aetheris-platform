# AI continuation handoff — aetheris-platform — 2026-11-09

> **Snapshot type:** documentation-only owner checkpoint, based on connected GitHub read and recovered prior work. Not a certification, successful merge or release authorization. Re-check GitHub before acting.

## Canonical repository
- Repository: https://github.com/teldigi5-wq/aetheris-platform
- Protected/default baseline: `main`; this document is proposed on review branch `docs/ai-continuation-20261109`, without changing code or main.
- Most recent relevant open draft PR: [#406](https://github.com/teldigi5-wq/aetheris-platform/pull/406)
- PR branch at inspection: `security/audit-importer-process-separation-20261107`
- Observed PR head: `aaaf7bcad0687a86672a07ca445f6940388cfcfa` (stale as soon as another commit lands; always verify).

## Status at handoff
34/34 repository master-roadmap complete; separate security assurance and Stage 45 remain NO_GO; physical PC BLOCKED_PENDING_HARDWARE.

**What exists:** Protected main already contains dashboard Node 22.23.4 and NGINX 1.31.8 / zlib source remediations via PRs #407 and #409, per docs/master-roadmap.md. This does not prove deployed containers or runtime acceptance.

**Outstanding gate:** Open draft audit membership and signed broker chain PRs #399–406, runtime boundary and gateway/Java trust assurance. Do not treat post-roadmap security maintenance as a new Stage 35.

## Next milestones (do not restart earlier stages)
1. **M1:** Re-fetch protected main, PR #406 and its entire stacked base chain; identify independent merge gates and do not cherry-pick blindly.
   - Acceptance: linked exact source commit, reproducible command and observed result; clearly mark not-run/blocked; zero secrets in logs.
2. **M2:** Review JWT/RS256 tenant authority, RabbitMQ producer/listener role separation, PG16 signed membership and revocation tests against exact proposed head.
   - Acceptance: linked exact source commit, reproducible command and observed result; clearly mark not-run/blocked; zero secrets in logs.
3. **M3:** Re-run relevant CI, CodeQL and two-cycle gateway recovery checks on the actual integration commit; investigate issue #312 and Java trust source/host mismatch.
   - Acceptance: linked exact source commit, reproducible command and observed result; clearly mark not-run/blocked; zero secrets in logs.
4. **M4:** Obtain owner-controlled deployment/TLS/credential/DR and physical-hardware evidence before any release promotion.
   - Acceptance: linked exact source commit, reproducible command and observed result; clearly mark not-run/blocked; zero secrets in logs.

## Resume protocol for any AI or engineer
1. Read this document, README, existing roadmap and referenced material; inspect the newest active PR(s), all predecessor bases, issues, Actions, CodeQL and actual branch head. Newer GitHub evidence overrides this dated snapshot.
2. Run `gh repo view teldigi5-wq/aetheris-platform`, `gh pr view 406 -R teldigi5-wq/aetheris-platform --json state,isDraft,baseRefName,headRefName,headRefOid,mergeable,statusCheckRollup`, `gh pr list -R teldigi5-wq/aetheris-platform --state open --limit 100`, and `git fetch --all --prune` in a clean isolated checkout.
3. Use PR head SHA as evidence identity; distinguish **proposed / implemented / locally tested / CI verified / merged / deployed / owner accepted**. Never transfer test results across commits.
4. Preserve all active draft branches, CI mirrors, protected main, real secrets and private customer/telemetry data. No destructive cleanup, no force push, no unconditional merge, no unexpected network, payment or deployment.
5. Only propose a small reviewed next change after confirming it is absent and necessary. Record SHA, test command, outcome and blockers in the same PR/doc; stop at NO_GO if missing operator evidence.
6. Prefer free local tools and existing CI; do not require a subscription or paid service to recover work. Keep production access separate from documentation.

## Relevant records
- `docs/master-roadmap.md`
- `docs/gateway-recovery-reproducibility-20261109.md`
- `docs/java-container-trust-20261109.md`
- https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/303

## Continuity note
Hundreds of historical branches may exist. They are **not** all implementation targets. The active PR/branch DAG and protected default branch determine the next work; preserve history rather than mass-merging or rewriting every branch. Cross-repo status and full machine-readable inventory commands belong in the companion account-level handoff bundle.
