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


## 2026-11-09 upstream candidate refresh-safe reconciliation checkpoint — NO SUBMISSION

This is a **new read-only upstream process review**, separate from the older
44-file validator dry run above. At the time of inspection, the real public
`xerj-org/xerj:corpus-hub` branch still pointed at commit
`708e974516a4ba1ba9bfa312aff164041997cfd6`. The existing author
candidate
[`teldigi5-wq--aetheris-platform.json`](https://github.com/xerj-org/xerj/blob/corpus-hub/tools/xerj-code/hub/candidates/authors/teldigi5-wq--aetheris-platform.json)
still references **older full-repository pin**
`908ac0a52b8c912af31195e60e7403cc6b61e691`, and its October
author-email consent assertion **does not cover** the distinct filtered
40-file snapshot proposed in Aetheris draft PR #376. No upstream
repository file has been edited, and no new inclusion consent has been
given or inferred.

**New material process finding:** XERJ's live
[`hub/README.md`](https://github.com/xerj-org/xerj/blob/corpus-hub/tools/xerj-code/hub/README.md)
says the `candidates/authors/` manifests and `CONTRIBUTORS*.jsonl`
ledgers are regenerated by the outreach publisher. A manual edit to the
existing author-candidate file could be overwritten on the next refresh.
A durable owner-requested re-pin or source-scope change is instead
reviewed by a **XERJ maintainer in a public issue** and, after approval,
represented in the existing
[`candidates/owner-overrides.json`](https://github.com/xerj-org/xerj/blob/corpus-hub/tools/xerj-code/hub/candidates/owner-overrides.json)
with a compatible manifest edit **and** contributor-ledger edit.
[`owner_overrides.py`](https://github.com/xerj-org/xerj/blob/corpus-hub/tools/xerj-code/hub/candidates/owner_overrides.py)
re-applies approved changes; its `--check` mode fails if a refresh
loses them. **Do not bypass the maintainer/issue approval by writing an
override directly.**

The authoritative XERJ
[`validate_hub.py`](https://github.com/xerj-org/xerj/blob/corpus-hub/tools/xerj-code/hub/validate_hub.py)
requires an author-candidate filename to match its `corpus` slug,
exactly one GitHub repository URL, a full 40-character source pin, a
licence string and appropriate human review metadata. It also requires
a matching row across the `CONTRIBUTORS*.jsonl` files, **including the
same repository URL and the revised pin**. An amendment that updates
only `repos[0].sha` without updating the ledger pin would be rejected.
The candidate area permits `review.use=UNREVIEWED` for an unapproved
draft, but the *live* Lane A manifest requires a genuine human
`review` block, and must not inherit old consent or fabricate one.

**Correct future handoff sequence — all dependent on real approval:**

1. Owner privately resolves the historical test-JWT live-key-use question
   or maintains HOLD; file-level privacy, originality, rights and licence
   review for the exact 40 files must be genuinely completed. Decide the
   exact seven sparse selectors and explicitly consent to this immutable
   source pin as historical/non-production reference content.
2. Recheck the **current** XERJ `corpus-hub` tip immediately before
   preparing a submission; review any changed candidate, validator or
   owner-override schema. Preserve the existing slug
   `teldigi5-wq--aetheris-platform`; do not register a duplicate.
3. Prepare the **owner request for a maintainer issue** explicitly stating
   the new pin, 40-file seven-directory scope, excluded YAML files,
   source classification and any consent/rights decision. Do not claim
   this issue, consent, or maintainer approval already exists.
4. Only after XERJ maintainer approval, apply an owner-override record
   with one matching manifest and contributor-ledger pin/source revision,
   and run `owner_overrides.py --check`, `validate_hub.py` and the
   XERJ native manifest/consumer CI against the **fresh** upstream tip.
   Keep build-receipt and review metadata truthful; do not promote
   `UNREVIEWED` into a live manifest.
5. Proceed with the reviewed Lane A submission only after owner release
   approval and required upstream checks. Production Aetheris deployment
   is a distinct process and remains NO_GO.

**Verified mechanical work stays limited to the source preflight:**
40 historical Git blobs / 66,617 blob bytes, 213 searchable local XERJ
records from a disposable preview. The prior preview is **not** an
approved live Hub manifest, signed artifact, official Hub physical-index
size, a human rights/consent receipt or XERJ maintainer acceptance.
The required historical JWT provenance job remains **failed by design**.

**Status:** no XERJ owner/maintainer request issued; no upstream changes;
existing candidate unmodified. Formal XERJ **2/6 complete, 4 pending,
publication HOLD**. The existing Aetheris draft proposal
`docs/integrations/xerj-upstream-amendment-draft.json` remains
`DRAFT_UNREVIEWED_NOT_SUBMITTABLE`.
