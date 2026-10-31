# Aetheris secret-scanning regression — review checkpoint

**Candidate:** [Draft PR #355](https://github.com/teldigi5-wq/aetheris-platform/pull/355), stacked on [credential/network hardening PR #315](https://github.com/teldigi5-wq/aetheris-platform/pull/315). The replaced draft [PR #353](https://github.com/teldigi5-wq/aetheris-platform/pull/353) was closed unmerged after its historical secret scan correctly rejected test-source PEM headers. [Release closure PR #322](https://github.com/teldigi5-wq/aetheris-platform/pull/322) remains **NO_GO**.

## Security objective

Block newly introduced secrets in pull requests and pushes to main, including secrets that appear only in intermediate commits, merge-conflict resolutions, or files whose Git attributes suppress ordinary patch content. This is a **new-commit regression gate**, not proof of a complete historic, runtime, or third-party-index audit.

## Threats and countermeasures

| Threat | Current countermeasure | Evidence |
| --- | --- | --- |
| Known-format secret in an added file | Gitleaks v8.30.1 with default rules, redacted logs | Disposable generated-RSA-key detection |
| Secret committed then removed before PR ends | Scan every newly introduced commit, not only the final worktree | Disposable add-and-delete history test |
| Secret added only in a merge-conflict resolution | Force merge diffs with --diff-merges=first-parent | Disposable merge-only RSA-key proof; offline Git merge test |
| Attacker adds a -diff rule or relies on binary-heuristic suppression | Force --text in Git log options | Disposable RSA key in file marked -diff; offline synthetic Git patch test |
| External diff helper or text conversion alters diff output | Pass --no-ext-diff and --no-textconv | Flag assertion in synthetic shell-wrapper tests |
| Incomplete fetch or invalid event base | Full checkout; validate nonzero base SHA, ancestor and nonempty range | Fail-closed regression tests |
| Attacker adds broad Gitleaks ignore/config | Reject root .gitleaks.toml and .gitleaksignore; ignore inline gitleaks:allow | Override regression tests |
| Malicious token exposure through CI | contents:read, no persisted checkout credentials, no production secrets, pull_request not pull_request_target | Workflow source inspection |
| Scanner binary altered in transit | Pinned upstream release archive and checked published SHA-256 | CI install step |

The workflow scans before running any repository-supplied Python test suite. Disposable keys and redacted detector proof reports stay under a runner-local temporary directory and are deleted by an EXIT trap. No test key material is committed to Aetheris.

**Performance trade-off:** --text can include contents from binary-looking Git diffs. Time and scan-size budgets should be monitored on real pull requests; a timeout or scanner error must fail the required check rather than silently skip files.

## Review and rollout gates

1. Verify the **latest exact-head** PR #355 workflow result, including all synthetic tests and real detector proofs. Do not reuse older head evidence after a new commit.
2. Review the complete workflow, scripts, tests and this document. Workflow/script authors can change their own security controls until independent review and branch-policy enforcement are active.
3. Integrate the selected reviewed candidate only through an approved release sequence. Do not independently merge the overlapping #313, #315 or #322 release candidates.
4. **Only when the gate is available on the intended protected line**, add the exact check name **gitleaks-new-commits** to the active **Protect stable main** ruleset. Keep all existing required checks and strict up-to-date behavior. Track owner/admin work in [issue #354](https://github.com/teldigi5-wq/aetheris-platform/issues/354).
5. Review administrator bypass permissions, independent approval and CODEOWNERS enforcement, proportionate to available trusted reviewers. Avoid a configuration that deadlocks the solo repository; document any temporary exception.
6. Preserve **NO_GO** until external release-gate certification, live credential rotation/reuse proof, data recovery and migrations, direct service authorization, supply chain and Java/CA evidence are independently approved.

## Audit evidence and limitations

- Owner-run Gitleaks historical fetched-ref scan (31 October 2026): 11 findings in test-related paths (8 generic-api-key, 3 private-key). These do not establish 11 live leaks, nor certify absence of production credential reuse.
- User screenshot: GitHub secret scanning showed zero **open** alerts at the time checked; resolved alerts and other providers need separate review.
- Existing main-based Compose files had known credential defaults and published service ports; remediation is in PR #315, still unmerged and not deployment-verified.
- Docker was inaccessible in the user's WSL environment. This does not establish production isolation.
- Source-only release closure is **NO_GO**. Passing Actions or scanner checks alone is not production authorization.
- This gate does not cover deleted/unreachable remote refs, untracked generated artifacts, previous public forks, XERJ copies, OCI images, cloud secret stores, or live operator environments. Unknown secret formats and files exceeding scanner limits may evade pattern-based detection.

## Progress chronology

- Initial draft #353 introduced new-commit scanner and synthetic proof. Historical scanning correctly blocked two later test-source PEM markers; draft closed **without merge or force-push**.
- Replacement #355 created from the exact PR #315 base with one clean initial commit, retaining the guarded scanner. GitHub Actions run 41197266181 passed: 8 synthetic tests and real harmless/add-delete/merge-only detection.
- Follow-up candidate forces textual patches despite -diff attributes and disables external diff/text conversion. A ninth synthetic regression test and fourth live detector proof were added. **Do not mark this follow-up verified until its exact-head GitHub Actions run passes.**

All work remains in draft review. No protected-main merge, runtime credential rotation, or production deployment is implied.
