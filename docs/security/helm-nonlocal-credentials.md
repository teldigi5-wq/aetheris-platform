# Aetheris Helm non-local credential validation — review candidate

**Scope:** This small draft is based on the unchanged credential/network hardening candidate [PR #315](https://github.com/teldigi5-wq/aetheris-platform/pull/315). It changes Helm render-time credential validation only. It does **not** merge to main, deploy, rotate real credentials, inspect cluster RBAC, run migrations, or certify production. [Release closure #322](https://github.com/teldigi5-wq/aetheris-platform/pull/322) remains **NO_GO**.

## Risk addressed

The existing non-local Helm gate rejected specific known demo credential strings and unpinned image tags, but did not reject different empty, short, or reused JWT, PostgreSQL, and RabbitMQ passwords. An installer could supply values that differ from known demo defaults yet remain unsafe. Redis already required an independent nontrivial password across all modes.

## Proposed fail-closed contract

- **Non-local mode only** (global.allowInsecureLocalDefaults=false): JWT, PostgreSQL, and RabbitMQ credentials each require at least **32 non-whitespace characters** and must all be distinct from each other.
- Redis retains the existing 32-character minimum and independent-secret checks against the three other credentials in **every mode**.
- A non-local RabbitMQ username must be explicitly nonempty. The fixed local demo username is not treated as a password.
- Retain existing checks blocking exact known local/demo secrets and non-digest-pinned container images.
- Local/demo defaults remain available only through explicit values-local.yaml opt-in and **must not be used** in a shared, public, or production cluster.
- **Length is not entropy.** Operators must use independent unpredictable passwords generated with a CSPRNG and provisioned from a protected secret store, not values checked into source, CLI arguments, or CI artifacts.

## Test evidence policy

tests/test_helm_nonlocal_credentials.py calls the **real Helm renderer** using ephemeral generated test-only passwords and synthetic digest-pinned image references. It verifies:

1. Strong, distinct non-local credentials render successfully and the chart includes NetworkPolicy resources.
2. Empty, short, and whitespace-only JWT/PostgreSQL/RabbitMQ credentials fail.
3. Reuse among JWT, PostgreSQL, and RabbitMQ passwords fails.
4. Redis reuse with any other credential fails.
5. A blank non-local RabbitMQ username fails.
6. Known demo credentials remain rejected for non-local mode.

Each test uses a restricted-permission temporary values file and captures rendered Secrets in memory; it must not print, persist, or upload actual Secret values. A CI failure must not echo rendered manifests.

## Release and operations caveats

- Helm rendering cannot verify live Kubernetes Secrets, workload identity, secret rotation, tenant isolation, image provenance, TLS trust, ingress enforcement, or runtime configuration.
- Helm materializes Kubernetes Secret values in rendered output and release metadata. Protect the deployment controller, cluster release storage, encryption at rest and RBAC. Avoid passing credentials via command-line --set arguments.
- Predictable 32-character strings can still pass the minimum length test. Secure random generation and protected secret delivery remain necessary.
- Rotating existing PostgreSQL/RabbitMQ credentials requires a data-preserving operational plan. Updating environment variables alone does not automatically rotate persisted database credentials.
- No operator release evidence or owner approval has been supplied. This slice is **review-only**, and the overall release remains **NO_GO**.

## Rollout checklist

- [ ] Run existing Helm source tests and new real-render rejection/acceptance tests on the **exact PR head**.
- [ ] Review and reconcile this small patch into the selected security release lineage without merging overlapping candidates independently.
- [ ] Verify real Kubernetes preflight, runtime credential rotation, ingress/NetworkPolicy enforcement and release approvals separately.
- [ ] Keep the wider security release checklist accurate; do not equate passing CI with production certification.
