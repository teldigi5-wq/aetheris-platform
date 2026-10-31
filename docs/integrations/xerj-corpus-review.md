# Aetheris → XERJ Corpus Hub: pinned source review candidate

**Status:** DRAFT. No XERJ registration, OAuth consent, external Hub pull request,
release publication or production deployment has been performed.
**Tracker:** https://github.com/teldigi5-wq/aetheris-platform/issues/360
**Date:** 2026-10-31.

## Purpose and scope

Proposed corpus slug: aetheris-java-service-boundaries.

"An agent investigating Java microservice boundaries would query this corpus
for Gateway, Identity, User and Audit integration decisions, event flows,
tests and token-flow architecture, with precise file/line citations."

Source: https://github.com/teldigi5-wq/aetheris-platform
Exact candidate commit (never use an unpinned branch):
57ad3e0e602efa6feb275328254151b959a3499b

The proposed sparse checkout contains only these six root directories:

- architecture/
- gateway/
- identity-service/
- user-service/
- audit-service/
- docs/security/

The GitHub recursive tree at the exact commit reported **65 Git blobs and
129,060 aggregate Git blob bytes**. These are source-tree measurements;
they are *not* a completed XERJ index build or pack size. No symlink or
submodule was present in those paths in the initial tree inspection.

Everything else is intentionally out of initial XERJ scope: build-evidence,
dashboards, workflows, other tools, integration fixtures, observability and
deployment manifests. A future expansion must trigger a new security and
licence review; don't silently include the whole repository.

## Preliminary source inspection (not a Gitleaks audit)

The initial file-name and narrowly defined content-pattern screen covered
65/65 selected source files at the pin. It found no obvious PEM private-key
headers, GitHub/AWS provider token patterns, or interpolated secret fallback
patterns among its checks. It found 11 synthetic @aetheris.local email
references in two unit-test files and no reason to treat them as real
customer contacts. **This inspection cannot rule out generic API keys,
low-entropy secrets, undocumented credential formats, PII or secrets in
previous commits.** The full checksum-pinned Gitleaks directory scan on
the exact archived source is part of the draft CI workflow, and it must
pass before a public submission may be considered. Scanner failures must
fail closed, not be bypassed by ignore lists.

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

A public corpus must label this as a **historical, non-production reference
implementation**. It must never promise secure-by-default production
deployment or advocate copying its known-unsafe defaults. A stronger future
pin may be preferable once a reviewed release lineage is merged.

## Next acceptance gates

- [x] Narrow candidate source scope and immutable 40-hex Git pin selected for review
- [x] Git tree metadata counted for selected paths
- [x] Preliminary 65-file filename/regex screen performed (not a secret audit)
- [ ] Exact-head hosted workflow must verify Git tree/pin and Gitleaks scan
- [ ] Human review of the pinned source for sensitive info and all licensing
- [ ] Decide whether to publish this historical source or wait for hardened main
- [ ] Build the actual XERJ corpus, measure indexed file/byte counts, verify
      citations and local queries, and validate the real Hub Lane A manifest
- [ ] Explicit owner approval of exact source snapshot/manifest and public
      XERJ submission; then XERJ maintainer review/approval

**None of these steps merge existing Aetheris release candidates or change
production. The operational release remains NO_GO.**
