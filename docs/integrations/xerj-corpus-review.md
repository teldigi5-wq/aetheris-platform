# Aetheris → XERJ Corpus Hub: pinned source review candidate

**Status:** DRAFT. No XERJ registration, OAuth consent, external Hub pull request,
release publication or production deployment has been performed.
**Tracker:** https://github.com/teldigi5-wq/aetheris-platform/issues/360
**Date:** 2026-10-31.

## Purpose and scope

Proposed corpus slug: aetheris-java-service-boundaries.

"An agent investigating Java microservice boundaries would query this corpus
for Gateway, Identity, User and Audit integration decisions, event flows,
application boundaries and token-flow architecture, with precise file/line citations."

Source: https://github.com/teldigi5-wq/aetheris-platform
Exact candidate commit (never use an unpinned branch):
57ad3e0e602efa6feb275328254151b959a3499b

The proposed sparse checkout contains only these six root directories:

- architecture/
- gateway/src/main/
- identity-service/src/main/
- user-service/src/main/
- audit-service/src/main/
- docs/security/

The GitHub recursive tree at the exact commit reported **44 Git blobs and
75,323 aggregate Git blob bytes**. These are source-tree measurements;
they are *not* a completed XERJ index build or pack size. No symlink or
submodule was present in those paths in the initial tree inspection.

Everything else is intentionally out of initial XERJ scope: build-evidence,
dashboards, workflows, unit/integration test fixtures, other tools,
observability, root deployment files and deployment manifests. A future expansion must trigger a new security and
licence review; don't silently include the whole repository.

## Source security screen and strict re-scope

An initial 65-file candidate underwent a limited file-name/regex screen.
The first **real checksum-verified Gitleaks directory scan** failed with one
generic-api-key finding in Gateway's JWT unit test: a hard-coded 32-character
synthetic HMAC signing fixture. This did not establish production credential
reuse, but it **blocked** indexing that full test-inclusive candidate.
The failed CI evidence remains available in PR #361 (run 41209628270).

The revised corpus explicitly includes **application source** and selected
architecture/token-flow documents, not test fixtures. It uses only six
sparse directories and does not grant a Gitleaks waiver or suppress
findings. This is a scope and use-case change, not a declaration that
the original fixture is a real or harmless production credential.
All 44 selected files were previously among the 65 with limited
regex screening. They require a fresh *real Gitleaks run* on the exact
44-file archive before a public corpus may be proposed.

These screens do not rule out generic or low-entropy secrets, unknown
credential formats, PII or earlier historical commits. A human must
review the actual selected content and the existing historical findings.

## Metadata, privacy and attribution-marker preflight

All **44 selected Git blobs** were inspected with a limited additional
content review at the pinned source commit. The review found:

- **0 email-address matches** within these selected files. The previous
  11 synthetic aetheris.local emails were in test files now excluded.
- **0 local-user home-directory path matches** for Unix/macOS/Windows
  home-path formats checked, and **0 literal password/secret assignments**
  matching the limited signature used for the review.
- **0 third-party copyright, SPDX header or embedded licence notices**
  matching the selected notice patterns. This does *not* prove authorship
  or eliminate the possibility of copied/derived source.
- URL hosts in the reviewed files were limited to localhost and
  development/service-name hosts such as identity-service, user-service,
  audit-service and orchestrator-service; none was identified as an
  external credentialed production endpoint. Source configuration values
  use environment placeholders rather than checked-in production secrets.

The workflow now includes a **second, independent, fail-closed source
metadata review** at the immutable pin. It checks for unexpected email
addresses, machine-specific home paths, licence/copyright markers,
private-key headers and unreviewed URL hosts. It runs **after** the
checksum-pinned Gitleaks scan. Its checker includes negative self-tests
so basic patterns cannot be silently removed without failing CI.

This is a **mechanical privacy/metadata indicator scan only**. It does
not establish that a real secret was never used elsewhere, verify every
piece of personally identifiable information, or replace human approval.
False negatives remain possible, and every scope/pin change needs renewed
review. The upstream XERJ contributor consent recorded for an older
full-repository pin cannot be silently reinterpreted as consent for the
revised 44-file version.

## Licensing and human approval

GitHub currently recognizes Apache-2.0 for the source repository, and
its root LICENSE begins with the Apache License, Version 2.0. This is
evidence of the repository-level declaration, **not** an independently
completed review of third-party code, generated examples or all per-file
notices. XERJ explicitly requires a person to read the actual licences
and supply the final review block. No one has supplied that human
attestation for this candidate.

Do not fill review.by, review.at or review.note as if human review
were already performed. Do not use fabricated values for cloned_at,
indexed files or indexed bytes. Populate the actual Hub Lane A
manifest only from a successful XERJ build and explicit licence sign-off.

Current upstream canonical guidance (verify again before submission):
https://www.xerj.org/docs/corpus-hub
Target branch: https://github.com/xerj-org/xerj/tree/corpus-hub
Manifest template: tools/xerj-code/hub/TEMPLATE.json
Validator: python3 tools/xerj-code/hub/validate_hub.py
Public contribution is a PR to XERJ's **corpus-hub** branch, not its main
branch, and is separate from an Aetheris PR.

## Why this pin is not a security best-practice endorsement

Platform main at the candidate pin does **not** contain later draft-only
credential/network hardening, Gitleaks enforcement, RS256/mTLS route fixes,
authoritative tenant membership or release closure. For example:

- Draft credential hardening: PR #315
- Draft secret regression gate: PR #355
- Draft non-local Helm credential policy: PR #356
- Draft advanced tenant revocation and MVCC tests: PR #359
- Production closure: PR #322, still NO_GO
- Governance status check required on main: issue #354, still unresolved

A public corpus must label this as a **historical, non-production application-source reference**, without unit-test fixtures. It must never promise secure-by-default production
deployment or advocate copying its known-unsafe defaults. A stronger future
pin may be preferable once a reviewed release lineage is merged.

## Next acceptance gates

- [x] Narrow candidate source scope and immutable 40-hex Git pin selected for review
- [x] Git tree metadata counted for selected paths
- [x] Initial 65-file screening; real Gitleaks detected one synthetic test fixture
- [x] Curated 44-file application-only scope, without scanner ignores
- [ ] Exact-head hosted workflow must verify Git tree/pin and Gitleaks scan
- [ ] Exact-head content metadata/privacy CI verification (not yet signed off)
- [ ] Human review of the pinned source for sensitive info and all licensing
- [ ] Decide whether to publish this historical source or wait for hardened main
- [ ] Build the actual XERJ corpus, measure indexed file/byte counts, verify
      citations and local queries, and validate the real Hub Lane A manifest
- [ ] Explicit owner approval of exact source snapshot/manifest and public
      XERJ submission; then XERJ maintainer review/approval

**None of these steps merge existing Aetheris release candidates or change
production. The operational release remains NO_GO.**
