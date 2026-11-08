# XERJ Stage 3 — reachable Git-history JWT test fixture audit

**Security and publication status: REVIEW ONLY / NO HUMAN APPROVAL.**

The earlier 44-file XERJ source-scope Gitleaks and metadata preflights scanned an immutable Git-tree snapshot, and the known synthetic 32-byte HMAC JWT signing fixture was verified to occur only in its original unit-test file at that one commit. This did **not** answer whether exactly the same bytes appeared in reachable historical Git objects. Human historical credential reuse, production deployment secrets, copies outside Git and licence review all remain outstanding.

The new `scripts/security/verify_xerj_fixture_history.py` parses the **known synthetic fixture out of the exact pinned Gateway unit test** without displaying it; then uses `git rev-list --objects --all` and streaming `git cat-file --batch` to examine distinct Git blobs reachable from checkout refs. It obtains the set of historical original-file blob IDs from Git's path history and requires that no other distinct reachable blob contains those exact bytes. A match outside those historical original-file object IDs makes the automated preflight fail. No matched secrets or source bodies are printed or saved. If reuse is found, only conservatively allowlisted, already-public Git path metadata and finding counts are reported for human triage; the workflow still fails closed. Bounded object sizes, object IDs, counts and output framing are validated. The original source pin and six sparse paths remain unchanged.

**Limits:** The scan covers only refs actually present in the full-history GitHub Actions checkout, not unreachable/pruned Git objects, other repositories, external archives, fork/private histories, deployed process environments, production secret managers or runtime credentials. Git's `rev-list --objects` provides a single representative path for each object ID, not a full path/rename index; an identical entire blob reused at a second path may not be independently identified. It tests **one known 32-byte fixture value**, not arbitrary secrets or semantic credential reuse. Even if green, it cannot prove that the fixture was never a real key, revoke a live credential, establish code authorship or authorize XERJ publication.

This CI step is an independent technical check under **formal XERJ Stage 3**, not completion of the human privacy/history review. The existing `docs/integrations/xerj-owner-publication-approval.md` checkboxes stay unchecked, and PR #361 retains an explicit unreviewed amendment, null author review fields and no XERJ Hub submission. Formal status remains **2/6 stages fully complete, four stages pending**. Aetheris production Stage 45 remains NO_GO. Do not silently broaden or repin the 44-file source corpus.

## 2026-11-07 failure finding

The first full-history run **FAILED** at the exact-byte gate: the known synthetic fixture appears in one or more distinct reachable Git blobs not identified as historical versions of the original Gateway unit-test source. This is a genuine **review blocker**, not permission to waive matching objects. A path-only diagnostic will be collected without emitting secret values, then reviewers must inspect the Git provenance and determine whether these are renamed test fixtures, harmless duplicates, or an actual credential-reuse incident. A historical public source change cannot remove a secret from existing clones; if genuine live credential reuse is discovered, owner-controlled rotation and incident response must precede publication. Neither the previous pin-scoped 44-file clean scan nor the historical finding establishes production exploitation.

## 2026-11-08 targeted Stage 3 triage and independent Stage 5 CI

The [historical review run #42325691510](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42325691510)
confirmed **10 extra distinct reachable Git blobs** with exact bytes equal
to the known Gateway *test* JWT-HMAC fixture, **outside the known origin-file
historical blob IDs**. Git's representative-path diagnostic printed only
four additional test-source paths, **not ten separate unique files**:

- `gateway/src/test/java/io/aetheris/gateway/MediaEndpointWebFluxIntegrationTest.java`
- `gateway/src/test/java/io/aetheris/gateway/MediaJobAuthWebFilterTest.java`
- `identity-service/src/test/java/io/aetheris/identity/JwtServiceIssuerAudienceTest.java`
- `orchestrator-service/src/test/java/io/aetheris/orchestrator/Phase12DirectExecutionAuthorityTest.java`

All four displayed paths are under `src/test/`, excluded from the
selected 44-file source corpus. The 10 Git *blob versions* could represent
multiple revisions at these or other paths. The reachable-history scan does
**not** establish all paths or commits that contained each blob; the
representative paths should not be treated as a complete provenance mapping.
No extra match was seen among 381 blobs at the **single pinned source commit**
in the prior source-tree check. Pinned source Gitleaks and metadata checks
also passed, but that does **not** establish that the fixture was never
a live JWT-signing secret or that all selected source is safe for publication.

**Human Stage 3 exit evidence, still needed:**

- [ ] Verify historical deployments, local and shared environment variables,
      secrets managers and other external configurations for reuse of this
      exact old fixture **without pasting any secret into an issue/PR**.
- [ ] Inspect the reachable historical test paths/commits, distinguish
      synthetic-only test reuse from actual credential usage, and record
      verifiable source/commit or sealed operator evidence.
- [ ] If any live use or possible exposure cannot be excluded, revoke/rotate
      the affected real credential in the appropriate owner-controlled system,
      validate consumers, and record a redacted incident/rotation attestation.
- [ ] Read the **exact 44 public source files** for non-pattern-detected
      personal/confidential material and acknowledge explicit exclusions;
      do not treat automated scanning as human privacy approval.
- [ ] Record an actual named owner's/reviewer's final scope-specific
      historical-key and privacy disposition. Do not invent approval,
      allowlist the ten blobs, suppress the failing check, or rewrite history.

**CI dependency correction:** the mandatory
`xerj-reachable-history-review-blocker` job now runs the exact same
fail-closed historical scanner after the pinned-source safety job.
The `xerj-pinned-source-scan` job must still pass immutable 44-file
source/Gitleaks/metadata/privacy/selected-commit fixture and nonfabricated
manifest checks. Only then may the *independent* technical Stage 5 upstream
validator and loopback-only local retrieval jobs run. Historical reuse
continues to fail the **overall workflow** until reviewed; Stage 5 technical
results cannot override the human Stage 3 HOLD, licence review or owner
consent. No live manifest or XERJ contribution is created.

**Formal progress remains 2/6 completed**, with Stages 3, 4, 5 and 6 open.
The 44-file pin, candidate identity and upstream author entry are unchanged.
