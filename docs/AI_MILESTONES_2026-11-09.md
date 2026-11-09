# Milestone and handoff evidence ledger — teldigi5-wq/aetheris-platform

> Snapshot: 2026-11-09. Proposed on documentation PR #418. All task boxes below are **to be revalidated** against current code; an unchecked box never means a previously completed stage should be repeated.

## Baseline known at snapshot
- [x] Existing README and AI continuation document read from dedicated documentation branch.
- [x] Documentation PR #418 confirmed draft/open at inspection.
- [ ] Full paginated branch/issue/PR/Actions inventory performed for every active branch (NOT certified).
- [ ] Exact-current implementation head and code tests revalidated (not performed by documentation updates).
- [ ] Merge or production release accepted (NOT asserted).

## Milestones from the existing handoff
- [ ] **M1 — revalidate:** Re-fetch protected main, PR #406 and its entire stacked base chain; identify independent merge gates and do not cherry-pick blindly.
  - Source branch/commit: _check current GitHub_
  - Results and evidence: _no new code tests in documentation-only PR_
  - Decision: **NOT ACCEPTED** without exact-source evidence; preserve any NO_GO.
- [ ] **M2 — revalidate:** Review JWT/RS256 tenant authority, RabbitMQ producer/listener role separation, PG16 signed membership and revocation tests against exact proposed head.
  - Source branch/commit: _check current GitHub_
  - Results and evidence: _no new code tests in documentation-only PR_
  - Decision: **NOT ACCEPTED** without exact-source evidence; preserve any NO_GO.
- [ ] **M3 — revalidate:** Re-run relevant CI, CodeQL and two-cycle gateway recovery checks on the actual integration commit; investigate issue #312 and Java trust source/host mismatch.
  - Source branch/commit: _check current GitHub_
  - Results and evidence: _no new code tests in documentation-only PR_
  - Decision: **NOT ACCEPTED** without exact-source evidence; preserve any NO_GO.
- [ ] **M4 — revalidate:** Obtain owner-controlled deployment/TLS/credential/DR and physical-hardware evidence before any release promotion.
  - Source branch/commit: _check current GitHub_
  - Results and evidence: _no new code tests in documentation-only PR_
  - Decision: **NOT ACCEPTED** without exact-source evidence; preserve any NO_GO.

## Operator checklist
- [ ] Re-read protected main, feature PR base/head, conflicts and review threads.
- [ ] Inspect independent CI/security policy logs for **the exact proposed commit**.
- [ ] Test failure paths and rollback; respect per-project threat model and privacy.
- [ ] Record zero-mandatory-cost route; ask before paid services or production actions.
- [ ] Update README status only after verified upstream change, not based on a draft.

## Evidence template (copy once per real change)
```text
Date (UTC):
Repo / branch / PR / exact source SHA:
Milestone and acceptance condition:
Existing prior evidence (with SHA):
Commands run / environment:
Results (pass/fail/blocked; links/artifact IDs):
Safety checks (secrets, permissions, tenant/data boundaries):
Reviewer / owner authorization:
Merge state / deploy state / real-world QA state:
Rollback path:
Next unique incomplete milestone:
```

**No stage advancement from a docs-only commit.** Previous certified/merged stages are retained; do not restart them solely because checklist boxes are open.
