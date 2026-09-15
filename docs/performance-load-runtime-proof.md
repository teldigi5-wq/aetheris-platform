# Aetheris Performance & Load Runtime Proof

This post-roadmap proof belongs to **Aetheris**, the underlying agent/runtime platform. It does **not** rename or redefine **Syntra**, which remains the personal AI assistant/persona that can use Aetheris capabilities.

## Purpose

The proof adds a reproducible hosted regression benchmark for the authenticated Aetheris gateway → identity/user-service path. It is designed to catch obvious performance regressions while preserving the repository's evidence discipline.

It records real measurements from a GitHub-hosted Ubuntu Docker Compose run:

- total requests
- successful requests and errors
- error rate
- elapsed profile duration
- throughput in requests/second
- p50 latency
- p95 latency
- p99 latency
- maximum latency
- gateway health samples while sustained load is active
- post-load service health
- post-load protected-read recovery
- read-only state integrity
- a Docker resource snapshot for the evidence artifact

## Load profiles

The checked-in contract currently defines two intentionally bounded profiles:

| Profile | Requests | Concurrency | Purpose |
| --- | ---: | ---: | --- |
| burst | 180 | 24 workers | Short concurrent authenticated-read pressure |
| sustained | 360 | 12 workers | Longer controlled authenticated-read pressure with live gateway health monitoring |

The harness first creates a real API consumer session through the gateway and warms the protected `/api/users` route so the timed profiles are not primarily measuring first-request initialization.

## Hosted guardrails

The guardrails are intentionally loose enough for shared GitHub-hosted runners and strict enough to detect severe regressions:

- maximum error rate: `1%` per profile
- maximum p95 latency: `3000 ms` per profile
- minimum throughput: `1 request/second` per profile

Raw measurements are always retained in the JSON report even when a guardrail fails.

These numbers are **CI regression guardrails**, not product SLAs or production SLOs.

## Runtime checks

The proof contract requires 16 checks:

1. gateway health is UP before load
2. identity-service health is UP before load
3. user-service health is UP before load
4. an authenticated API consumer session is created
5. warmup completes and the Redis users-list cache is materialized
6. the configured concurrent request set completes
7. the sustained profile completes
8. profile error rates remain within the hosted guardrail
9. profile p95 latencies remain within the hosted guardrail
10. profile throughput remains above the hosted floor
11. p50/p95/p99/max latency observations are recorded
12. gateway health stays responsive while sustained load runs
13. core services are healthy after load
14. a protected read succeeds after load
15. read-only state remains unchanged
16. the report retains the hosted-only truth boundary

## Evidence files

Source-of-truth inputs:

- `tools/performance_load_runtime_smoke.py`
- `build-evidence/runtime/performance-load-runtime-contract.json`
- `.github/workflows/performance-load-runtime-proof.yml`

Runtime outputs uploaded by CI:

- `build-evidence/runtime/performance-load-runtime-report.json`
- `build-evidence/runtime/performance-load-docker-stats.txt`
- `build-evidence/runtime/performance-load-compose-ps.txt`
- `build-evidence/runtime/performance-load-failure.log` when the job fails

## Truth boundary

Evidence class: `HOSTED_RUNTIME`.

A passing workflow means the checked Aetheris revision satisfied the declared regression guardrails in that specific GitHub-hosted Docker Compose run. It does **not** prove:

- production capacity
- a production SLA/SLO
- Internet-scale concurrency
- cross-region behavior
- real-user traffic behavior
- target-PC performance
- GPU/RTX 4050 performance
- Windows performance
- physical-PC validation
- final deployment sizing
- Syntra voice/UI responsiveness

Physical owner-PC validation therefore remains `BLOCKED_PENDING_HARDWARE` until the intended machine is available.
