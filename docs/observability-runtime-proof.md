# Observability Runtime Proof

Aetheris now has a second hosted runtime evidence layer dedicated to observability.

The purpose of this pass is to prove that the repository's telemetry stack is not just a collection of configuration files: selected metrics, logs, traces and Grafana provisioning must become observable in a clean GitHub-hosted Docker Compose environment.

## Evidence class

`HOSTED_OBSERVABILITY`

This means the evidence is produced by GitHub Actions on Ubuntu using Docker Compose with the `observability` profile enabled.

It does **not** mean:

- production readiness;
- real-user traffic validation;
- physical-PC validation;
- long-duration reliability or capacity validation;
- validation of the optional browser/operator/local-model features.

The owner-machine truth boundary remains `BLOCKED_PENDING_HARDWARE` where physical evidence is required.

## Components under proof

The workflow starts the core platform plus:

- Prometheus;
- Grafana;
- Loki;
- Tempo;
- OpenTelemetry Collector;
- Grafana Alloy.

## Runtime contract

The machine-readable contract is:

- `build-evidence/observability/observability-runtime-contract.json`

The executable proof harness is:

- `tools/observability_runtime_smoke.py`

The CI workflow is:

- `.github/workflows/observability-runtime-proof.yml`

The contract requires 13 observations:

1. Prometheus readiness;
2. Loki readiness;
3. Tempo readiness;
4. Grafana readiness;
5. OpenTelemetry Collector running;
6. Alloy running;
7. all five configured Aetheris Prometheus scrape jobs reporting `up`;
8. representative authenticated traffic generated;
9. a positive HTTP request counter returned through Prometheus query API;
10. Tempo reporting received spans;
11. Loki exposing Aetheris service labels from Alloy-collected Docker logs;
12. Grafana exposing provisioned Prometheus, Loki and Tempo datasources;
13. Grafana exposing at least one provisioned dashboard.

## Why this evidence matters

The static portfolio proof can show that telemetry configuration exists. This workflow asks a stronger question: **does telemetry actually traverse the running stack?**

A green run demonstrates, for that exact commit and CI environment, that:

- application metrics are being scraped;
- OTLP traces reach Tempo through the collector path;
- Docker logs reach Loki through Alloy;
- Grafana starts with the expected datasources and dashboard provisioning.

If any observation is absent, the workflow fails instead of converting configuration presence into an operational claim.

## Evidence hierarchy

Aetheris now distinguishes three useful evidence levels:

- **REPOSITORY evidence** — implementation/configuration exists and is machine-checked;
- **HOSTED_RUNTIME / HOSTED_OBSERVABILITY evidence** — selected behavior is observed on GitHub-hosted infrastructure;
- **PHYSICAL_MACHINE evidence** — still required for target-PC-dependent functionality.

That distinction should remain explicit in the README, interviews and future releases.
