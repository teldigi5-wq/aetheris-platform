# Dependency scanning and repository security assessment

This proposal adds two **independent** scanners; it does not replace CodeQL, dependency lockdown, the existing required status checks, or the physical-PC validation gate.

- **OSV-Scanner v2.6.0** checks new vulnerabilities on PRs against `main` and does a full dependency check on `main` and weekly. The workflow uses the upstream reusable workflows pinned to commit `a345acffa64b0eaede81a3d9aae6141214d9c8fc`. New findings fail the PR job; a scanner failure with no results is not treated as clean.
- **OpenSSF Scorecard v2.4.4** assesses repository practices weekly and on `main`. The upstream action is pinned to `2d1146689b8cda280b9bc96326124645441f03bc`. Results are uploaded as a SARIF artifact and to GitHub Code Scanning. `publish_results: false` avoids submitting optional public results.
- Both workflows have scoped GitHub permissions. They neither write repository source nor execute workstation/AI runtime commands.
- Existing CodeQL and core reproducibility CI stay unchanged.
- Scores and vulnerabilities require triage; passing scan results do not certify production readiness, physical hardware, or the external AI runtime.

## Validation before merge

1. Verify workflow YAML parses and the pinned upstream workflow refs are reachable.
2. Confirm OSV PR job runs on this PR and reports actual scan results; do not add it as required until the check has run successfully.
3. Confirm the Scorecard workflow can run from the default branch or explicit workflow dispatch after merge; do not claim it has already executed.
4. Review findings, and do not bypass branch protections or mark scans green through stub jobs.

Physical machine status remains `BLOCKED_PENDING_HARDWARE`.
