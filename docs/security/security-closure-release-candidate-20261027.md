# Aetheris security closure candidate — source audit and release conditions (27 October 2026)

**Source branch:** PR #315, `247f793e890d3ec2e9cfca6e525a71d684ec810e`, which already consolidates earlier draft Java/CA updates and Compose/Helm hardening. Parent PRs #309 and #313 overlap; **do not independently merge or deploy them alongside PR #315** without diff reconciliation. This child branch is a *review checkpoint*, not an authorized security release. Current decision: **NO_GO**.

## Closure workstreams and external sign-offs

1. **Consolidate & integrate:** Reconcile security stack PRs #296–#321 with the **main-based release** (boot3.3.3 versus later Spring 4 Java branches, legacy HMAC versus JWT RS256). Integrate and test only a selected compatible JWT model, not two incompatible parsers or overlapping certificate/Compose commits. Produce exact-head, full-workflow test results and reviewed diffs.
2. **Real service authorization (#251):** The current main-based release lacks backend JWT filters for direct User and Audit calls. Its Gateway filter validates tokens but direct-backend reachable clients have no equivalent enforceable authorization. Introduce actual resource-server validation, enforced role/scope and object-level owner/tenant checks with negative HTTP/IDOR tests. Network restrictions alone are not authentication.
3. **Historical migration & recovery (#242):** Three release candidate Compose variants explicitly use `SPRING_JPA_HIBERNATE_DDL_AUTO: update`. Obtain historical schema/volume inventory and owner-approved Flyway baseline, back up and restore an existing representative data set, then switch all relevant services to `validate` with versioned migrations; test fresh + seeded histories and failure cases. **Do not blindly change values to validate**, or delete persistent volumes.
4. **Operator/security certification:** Verify patched OCI digests and SBOM (issue #255), Jackson advisories and actual resolved version (issue #259), TLS truststore and running images (#309), secret/Redis/DB rotation after existing volumes (#312), signed emergency-write dual-control and independent immutable audit, real physical evidence and explicit owner signoff. Nine Stage45 runtime release gates and 13 safety blockers remain unresolved.

## Enforceable source-only release status

`scripts/security_closure_preflight.py` reads the three Compose files and two backend JWT filter source paths. It surfaces the current unreviewed automatic DDL and missing direct service filters in a JSON status. A CI test tries a fake source-perfect fixture and still requires `NO_GO`, because source presence and claimed approvals can never establish real-world operational safety. `--require-ready` always returns nonzero; `--assert-blocked` succeeds only when the program demonstrably reports `NO_GO`. **A green 'blocked as required' workflow is NOT an approval to merge into `main` or to deploy.**

## Separately progressing feature roadmap

Security closure should be handled in parallel with real Syntra E3.9 website-agent evaluation and E4–E9 feature development in `aetheris-ai-runtime`. Existing reported Qwen7B CSS-repair trials include real browser failures. Preserve evaluation integrity, hold-out fixtures, zero mandatory cost, and don't mark agent ability achieved because a test harness passes.

This candidate neither changes running service configuration nor grants emergency revocation access, creates migrations, rotates credentials, merges protected main, or certifies deployment. **NO_GO remains until independent evidence and owner action.**
