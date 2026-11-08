# XERJ contribution: scope-specific owner review and approval

**Not an authorization or consent receipt.** All checkboxes below start
unchecked. Owner identity, rights, licensing and scope approval must not
be inferred from a past repository licence, a previous automated email
response, GitHub public visibility, or a passing security scan.

## Exact source to be considered

- Source: https://github.com/teldigi5-wq/aetheris-platform
- Immutable source commit: 57ad3e0e602efa6feb275328254151b959a3499b
- Corpus proposal: aetheris-java-service-boundaries
- Paths: architecture/, gateway/src/main/, identity-service/src/main/,
  user-service/src/main/, audit-service/src/main/, docs/security/
- Git-tree measure: **44 blobs / 75,323 bytes**
- **New machine-assisted source-content triage:** `docs/integrations/xerj-pinned-content-review-findings.md`
  records specific historical YAML local JWT/database/Rabbit credential
  fallback lines, origin/CORS configuration context, token-flow cautions
  and architecture-status caveats. This is NOT live-secret detection,
  human copyright approval or evidence of a safe production deployment.
  In particular, indexed YAML fallbacks must never be promoted as
  recommended production credentials.
- **Complete per-file human review list:** `docs/integrations/xerj-44-file-review-inventory.md`
  enumerates all 44 pinned paths, SHA-1 Git blob IDs and byte sizes,
  with **zero** human-review checkboxes approved. CI checks the full
  inventory against the immutable source, but cannot read/approve rights.
- **Actual XERJ local index evidence:** 44 indexed files, 219 searchable
  records, 75,323 independently catalog-accounted content bytes in one
  dataset on a disposable loopback-only XERJ node. Physical index bytes
  and approved Lane A manifest metadata **remain unknown/null**.
- **Reachable Git-path classification:** 2,639 distinct root trees in the prior exact-head GitHub Actions run
  #42485143400 on `0777963a978d2e1a0b6c744421a3264737ec6053` (earlier
  runs counted fewer trees as reachable refs changed);
  10 additional matching historical test-signing-value blobs occur at
  four unique `src/test` paths and **zero non-test paths**. This
  narrows Git provenance only; live credential reuse is still unknown.
- Source classification: historic Java application and architecture reference,
  **not** a secure/production-certified implementation or complete test suite.
- The AI Runtime repository is a separate project and is not included.

## Evidence available, and what remains unknown

- [x] Gitleaks v8.30.1 real directory scan of the exact 44-file archive,
      no findings. A prior 65-file attempt was correctly blocked by a
      synthetic Gateway JWT unit-test fixture and was narrowed explicitly.
- [x] Mechanical email/home-path/unreviewed URL/notice patterns screened
      against all 44 files. False negatives and other PII are possible.
- [x] Real XERJ rc.100 local indexing and two Java file:line retrieval
      checks passed on a disposable localhost-only runner.
- [ ] Read and approve all 44 selected files for intended worldwide public
      search/reuse, including any confidential business context, personal
      details or sensitive operational patterns that pattern scanners miss.
- [ ] Verify the old 32-character JWT test fixture was **never a live key**;
      if it was, complete rotation and historical exposure review.
- [ ] Independently read the root Apache-2.0 licence at the exact source
      commit and confirm this repo owner has rights to license each selected
      file. Check third-party/generated/copy-derived code even where no
      in-file copyright notice appears.
- [ ] Confirm that the historical/non-production status and unmerged
      security protections are prominently disclosed in the public corpus.

## Stage 3 scope reconciliation: 44 versus 40 (NO decision or publication approval)

The **current authoritative review candidate on this draft PR #361 is still
44 files / 75,323 Git blob bytes** at immutable source pin
`57ad3e0e602efa6feb275328254151b959a3499b`. Its six path selectors
include four service `src/main/resources/application.yml` files. The
separate source-content findings identify historical local JWT signing,
PostgreSQL and RabbitMQ fallback defaults in these YAMLs; passing Gitleaks
does **not** turn such defaults into production-safe examples.

An existing isolated **draft child PR #376** proposes a **40-file / 66,617-byte**
candidate at the **same immutable source pin**, excluding exactly four
Git blobs:

| Excluded from the 40-file alternative | Git blob bytes |
| --- | ---: |
| `gateway/src/main/resources/application.yml` | 5,256 |
| `identity-service/src/main/resources/application.yml` | 1,134 |
| `user-service/src/main/resources/application.yml` | 1,258 |
| `audit-service/src/main/resources/application.yml` | 1,058 |
| **Total excluded** | **8,706** |

The 40-file variant is reproduced by the **seven exact directory
selectors** `architecture`, `gateway/src/main/java`,
`identity-service/src/main/java`, `user-service/src/main/java`,
`audit-service/src/main/java`,
`audit-service/src/main/resources/db/migration`, and `docs/security`.
At its own **exact HEAD** `107fa7ade0407c74dfc2154c64e30bfe0ce2b11a`,
the existing [Actions run #42158502931](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42158502931)
passed **2/2 jobs** (pinned-source scan and disposable local retrieval).
This is **not** Stage 3 or Stage 4 human approval. Do not reuse the
44-file candidate's **219 indexed records** as the 40-file index count:
that value was measured for a *different* artifact.

The 40-file child is based on an **older #361 revision**. It now diverges
from #361's newer privacy/history/manifest controls and must **not** be
merged or cherry-picked blindly. The currently approved scope remains
**none**; the tracked review scope is 44 until an explicit owner decision
and corresponding reconciled exact-head manifest, review inventory,
workflow and candidate amendment are prepared. Neither alternative is
production-certified; removing YAML does not repair historical Java
authorization flaws or establish rights.

**Finite owner decision (all UNCHECKED):**

- [ ] **Retain 44** only after reviewing and accepting the documented
      risks of making the four historic development-configuration examples
      retrievable, plus the normal privacy, rights and signing-key gates.
- [ ] **Prefer 40** to avoid publishing those four example fallback
      configurations; integrate the already-tested filtered subpaths into
      existing PR #361 without losing newer validators, then independently
      validate the final 40-file manifest and get fresh 40-file human review.
- [ ] **Defer both** and keep XERJ publication on HOLD pending an acceptable
      source revision and an independently reviewed immutable pin.

**Historical signing-key gate is unchanged for all choices.** On the
previous exact #361 HEAD, [run #42485143400](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42485143400)
passed the selected source scan, upstream negative validator and local
retrieval jobs, but the mandatory reachable-history fixture review
**failed by design**, keeping the overall workflow red. That scan found
10 additional distinct matching historical Git blobs across four
`src/test` paths and **zero non-test reachable paths**. This is
test-only *Git-path provenance*, not proof of never-live credential use.
An owner with access to actual historical environments/secret management
must make the redacted use/rotation determination. No human review,
licence signoff, consent receipt, Hub manifest or upstream approval is
inferred by this reconciliation.

## Existing XERJ draft: avoid duplicates

XERJ's official corpus-hub branch already has a **draft** author candidate:

https://github.com/xerj-org/xerj/blob/corpus-hub/tools/xerj-code/hub/candidates/authors/teldigi5-wq--aetheris-platform.json

That entry records a different, full-repository commit
908ac0a52b8c912af31195e60e7403cc6b61e691 (381 Git files,
1,976,295 bytes), without sparse directory paths. It asserts
author-consent-by-email in October 2026. That assertion has not been
independently verified here, and the old consent is not automatically
permission for this newer, narrower commit.

The desired next action is to **reconcile the existing XERJ draft**
through a verified owner/maintainer process, not create a duplicate corpus.
The XERJ maintainer controls their registry and may make a different
review decision or decline the candidate.

## Explicit new approval needed for publication

- [ ] I approve the specific 44-file source snapshot above for public
      XERJ Corpus Hub publication, subject to the stated attribution,
      security and historical-status notes.
- [ ] I approve reconciling the existing draft rather than submitting a
      competing duplicate.
- [ ] I confirm that the licence reviewer and review date to put into
      XERJ's required per-repository review block accurately reflect
      the actual person who reviewed the Apache-2.0 source.
- [ ] I authorize only a reviewable XERJ contribution PR or equivalent
      GitHub-linked contribution action, **not** production deployment,
      Aetheris PR merges or secret upload.

No check box may be marked by the assistant without the documented
owner/reviewer action. Even an owner approval does not certify production
security or remove the need for XERJ maintainer review and Hub validation.

## After signed-off decisions

1. Select the reviewer-confirmed SPDX value and reuse classification
   (adapt-with-attribution, approach-only, or mixed, as appropriate).
2. Use the XERJ CLI to generate a **real** corpus manifest with actual
   indexed records/bytes, pinned source and curated sparse paths.
3. Validate through the upstream corpus-hub branch validator and, when
   applicable, the XERJ consumer manifest tests.
4. Prepare a proposed amendment to the existing candidate for maintainer
   review. Preserve provenance and audit history; do not invent
   cloned_at or overwrite the old consent.
5. Publish only after explicit owner authorization and maintainer approval.

**Aetheris operational release stays NO_GO. No production change is implied.**
