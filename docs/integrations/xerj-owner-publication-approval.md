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
