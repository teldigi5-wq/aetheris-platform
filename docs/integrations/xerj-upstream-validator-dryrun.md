# XERJ upstream Lane A validator dry run — non-submittable preflight

**Status: DRAFT / NO PUBLICATION AUTHORIZATION.** This evidence belongs only to Aetheris Platform PR #361 and XERJ source-contribution issue #360, **not** the operational Aetheris Stage 45 release gates.

## Source identity and original safeguards

- Aetheris public historical Java reference stays pinned to **`57ad3e0e602efa6feb275328254151b959a3499b`**, restricted to six documented sparse directories, **44 Git files / 75,323 Git blob bytes**.
- The **existing** XERJ candidate is `tools/xerj-code/hub/candidates/authors/teldigi5-wq--aetheris-platform.json` on `xerj-org/xerj:corpus-hub`, not a live `hub/*.json` manifest. Its older full-repository pin is **`908ac0a52b8c912af31195e60e7403cc6b61e691`**. The older consent assertion is not automatically scope-specific consent for this proposed new pin.
- The schema-test baseline is the verified upstream `corpus-hub` commit **`708e974516a4ba1ba9bfa312aff164041997cfd6`**. It must be refreshed and inspected if the external branch changes.
- The existing draft amendment remains `DRAFT_UNREVIEWED_NOT_SUBMITTABLE` with null reviewer, consent, actual index counts and clone timestamp. It is not a valid XERJ Lane A submission.

## New technical Stage 5 preflight

The read-only Aetheris GitHub Actions preflight now includes a third independent job. It clones XERJ's *public* `corpus-hub` branch inside a temporary hosted-runner folder with filtered shallow objects, verifies the pinned SHA against the existing draft amendment, inspects the existing upstream author's candidate, and runs the **actual current upstream Python stdlib `validate_hub.py`** over the unmodified registry. No synthetic manifest is placed in the Aetheris source tree or pushed to upstream.

It then writes a clearly labeled **negative-test-only unreviewed manifest** to the isolated XERJ clone, runs the same upstream validator, and requires a nonzero result specifically attributable to the illegal `review.use=UNREVIEWED` live-manifest status. The ephemeral negative file is removed in a `finally` clause; the temporary clone is deleted by a shell trap.

The preflight fails closed if the upstream registry is no longer at the verified commit, the upstream candidate's earlier pin/content was changed, the proposal silently claims approval, the proposed sparse paths diverge from the pinned source, the upstream validator rejects its own baseline, or the upstream validator somehow accepts an unreviewed public Lane A manifest.

The test intentionally prints only **PASS/FAIL classification and no source passages, consent IDs, scan findings, keys, or raw validator output**. Actions checkout persists no GitHub credentials and job permissions are read-only.

## What this does *not* finish

- **Stage 3 — Human historical secret/privacy/source review: OPEN.** Mechanical Gitleaks checks on the exact source pin were previously green; a human must still confirm historical JWT fixture never served a live role and review non-pattern sensitive details.
- **Stage 4 — Human licence, provenance, attribution and exact-pin approval: OPEN.** No human `review.by`, `review.at`, reuse category or consent can be filled from tool evidence.
- **Stage 5 — True manifest / index count / consumer / maintainer validation: PARTIAL.** An actual XERJ registry validator dry run demonstrates schema compatibility and refusal of an unreviewed *live* manifest. No approved manifest has been built; actual XERJ indexed record and byte counts remain unknown, never substituted with Git blob metadata.
- **Stage 6 — Owner-approved upstream reconciliation and maintainer review: OPEN.** No changes to the existing upstream draft or a duplicate entry, no Hub PR, no publication or OAuth.

The formal tracker stays **2/6 complete; 4 stages remain**, even if this additional technical preflight passes. Operational release Stage 45 remains **NO_GO**. No merge, deployment or XERJ submission is implied.

**Test evidence only becomes verified when this updated exact PR #361 head finishes the GitHub Actions workflow successfully.**
