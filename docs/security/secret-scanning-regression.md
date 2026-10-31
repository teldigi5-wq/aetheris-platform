# Secret-scanning regression gate (review-only candidate)

This change adds a **new-commit** secret-exposure gate to every pull request and every push to main. It complements the existing Stage 22 source gate, CodeQL, and GitHub provider-specific secret scanning.

- The workflow runs Gitleaks **v8.30.1** from the official release after checking the archive's SHA-256 against its published digest.
- Checkout fetches complete history, persists no credentials, and uses read-only content permissions. The trigger is pull_request, never pull_request_target; no production secrets are provided.
- The script validates the base SHA, commit ancestry, and a nonempty scan range. Missing, rewritten, and ambiguous bases fail closed instead of silently skipping scanning.
- Newly introduced commits are scanned, including test files, docs, added-then-deleted secrets, and changes to workflows. Inline gitleaks:allow suppressions are ignored.
- Repository overrides named .gitleaks.toml or .gitleaksignore are rejected. A narrowly reviewed policy is required before introducing allowlists.
- All diagnostic output is redacted; no secret-containing findings report or SARIF is uploaded.
- This is a **regression** gate, not a replacement for historical scanning. The independently examined history contains 11 test-related detections; production credential reuse is not yet independently verified.
- Protect the check named **gitleaks-new-commits** as required in repository rulesets. A workflow alone does not enforce merge blocking.
- Require protected review of .github/workflows/** and scripts/security/** through CODEOWNERS or an equivalent policy, so a contributor cannot weaken their own security gate without review.

## Operator verification

1. Review the isolated draft PR and inspect the exact-head GitHub Actions run.
2. Independently verify detection using synthetic sample secrets in a disposable repository, never real production credentials.
3. Preserve the release closure **NO_GO** decision. Do not merge, deploy, or treat a green secret scan as production certification.
4. Separately verify runtime secrets, Docker port exposure, credential rotation, and every existing release gate.

**Residual risks:** pattern-based scanners miss some secrets; GitHub Actions may be unavailable due to account limits; deleted/unreachable history, build artifacts, external indexes, and live deployments require independent review. A green check is not proof of zero vulnerabilities.
