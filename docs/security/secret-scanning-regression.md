# Secret-scanning regression gate — audit continuation

**Scope:** draft PR #353 is stacked on credential-hardening PR #315. Neither candidate is approved for production release. PR #322's closure gate remains **NO_GO**.

This change provides a **new-commit** secret-exposure check for every pull request and push to main, complementing the existing Stage 22 source gate, CodeQL and GitHub provider-specific secret scanning.

## Controls

- Gitleaks v8.30.1 is downloaded from the official upstream release and the archive SHA-256 must match the published digest before extraction.
- Checkout fetches full Git history, does not persist checkout credentials, and uses contents:read only. The event is pull_request, **not** pull_request_target; no deployment credentials are passed to the job.
- The actual new-commit scanner runs **before any repository-supplied Python test suite**. This avoids executing PR-modifiable tests before the scanner.
- A nonzero, valid ancestor base SHA and nonempty commit range are mandatory; ambiguous or missing ancestry fails closed.
- All introduced commits are scanned, including tests and files added then removed. Inline gitleaks:allow suppressions are disabled, and repository-level .gitleaks.toml or .gitleaksignore overrides fail.
- Output is redacted; no secret-containing JSON reports or SARIF artifacts are published.
- After the real PR scan passes, a second **ephemeral detection proof** creates a disposable Git repository in runner-local temporary storage. It confirms that harmless commits pass, Gitleaks flags a newly generated RSA private key, and the same key remains detectable after a subsequent removal commit in the scanned history. The RSA key and redacted proof report are deleted by a shell EXIT trap. No key fixture is committed to Aetheris or uploaded.
- A synthetic seven-test offline suite then verifies shell-wrapper argument/range/ignore failure semantics.

## Acceptance and merge prerequisites

1. Verify exact-head GitHub Actions result for PR #353, including the real detector proof, not just a mocked scanner test.
2. Review workflow/script diff and enforce code-owner review if an independent trusted reviewer is available. Workflows and security scripts are themselves security-critical code.
3. Require the check named **gitleaks-new-commits** in the protected-main ruleset once the reviewed gate is integrated into the designated release lineage; retain all existing checks and strict branch freshness.
4. Track governance enforcement in issue #354. The repository's ruleset was observed to have 13 required checks, zero required approvals and an administrator-role bypass during the 2026-10-31 audit.
5. Preserve **NO_GO** until security closure includes operational proof of credential rotation, live deployment isolation, object/tenant authorization, approved database migrations, and the other nine documented release conditions.

## Known limitations

- The gate scans changes in the event's Git range. It does not certify all historic or unreachable commits, external CI artifacts, XERJ copies, or real runtime secrets.
- The prior fetched-ref historical audit detected 11 test-related patterns; no live leak is confirmed, but reuse outside tests has not been disproven.
- A PR author who can modify the workflow or scan script may try to undermine checks. Required independent review and guarded branch policies are essential; a successful check alone is insufficient.
- Network failure, a digest mismatch, scan errors or an unavailable checkout ancestry must **fail**, not silently pass.
- This is not an authorization to merge or deploy. No security workflow proves zero vulnerabilities.

## 2026-10-31 implementation checkpoints

- Draft PR #353 initial exact-head run 41195078982: 7 synthetic unit tests passed; Gitleaks scanned five new commits and found no leaks.
- Follow-up: reordered scan before repository tests and added real Gitleaks positive/negative proof using ephemeral keys, including an added-then-deleted secret. **Verify the latest exact-head CI before declaring this enhancement complete.**
- Issue #354 tracks required status-check enforcement and review policy. Ruleset settings have not been changed.
