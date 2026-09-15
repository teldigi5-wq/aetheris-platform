# Aetheris Performance & Load Runtime Proof

This post-roadmap proof belongs to **Aetheris**, the underlying agent/runtime platform. It does **not** rename or redefine **Syntra**, which remains the personal AI assistant/persona that can use Aetheris capabilities.

## Purpose

The proof adds a reproducible hosted regression benchmark for the authenticated Aetheris gateway → identity/user-service path. It is designed to catch obvious performance regressions while preserving the repository's evidence discipline and the gateway's security controls.

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
- authenticated-principal count per profile
- gateway health samples while sustained load is active
- explicit HTTP 429 evidence from a deliberate single-principal overload
- post-load service health
- post-load protected-read recovery
- read-only state integrity
- a Docker resource snapshot for the evidence artifact

## Gateway-aware load model

The protected `/api/users/**` gateway route intentionally uses a Redis-backed per-principal rate limiter with a `10 requests/second` replenish rate and `20 request` burst capacity. A benchmark that fires hundreds of requests through one token mostly benchmarks the limiter, not the downstream runtime.

The proof therefore keeps both truths visible:

1. **Policy-compliant aggregate load** is spread across multiple real users registered through the public gateway. Every request still carries a real JWT and passes the normal gateway authentication path.
2. **Policy enforcement** is tested separately by deliberately overloading one authenticated principal and requiring HTTP `429` responses.

The benchmark does not disable, increase, or bypass the production-defined gateway rate limiter.

## Load profiles

The checked-in contract currently defines two bounded policy-compliant profiles plus one overload probe:

| Profile | Requests | Concurrency | Principals | Purpose |
| --- | ---: | ---: | ---: | --- |
| burst | 180 | 24 workers | 20 | Short aggregate authenticated-read pressure while remaining below each user's burst capacity |
| sustained | 360 | 12 workers | 20 | Controlled aggregate authenticated-read pressure with live gateway health monitoring |
| rate-limit probe | 40 | 24 workers | 1 | Deliberately exceed one user's burst allowance and require HTTP 429 enforcement |

The harness also uses a separate warmup principal for untimed cache initialization. Registration traffic is intentionally paced below the identity route's own per-IP replenish rate so setup traffic is not confused with benchmark traffic.

## Hosted guardrails

The policy-compliant profiles use intentionally conservative GitHub-hosted regression guardrails:

- maximum error rate: `1%` per profile
- maximum p95 latency: `3000 ms` per profile
- minimum throughput: `1 request/second` per profile

Raw measurements are always retained in the JSON report even when a guardrail fails. The overload probe is evaluated differently: at least one request must be admitted and at least one request must be rejected with HTTP `429`.

These numbers are **CI regression guardrails**, not product SLAs or production SLOs.

## Runtime checks

The proof contract requires 17 checks:

1. gateway health is UP before load
2. identity-service health is UP before load
3. user-service health is UP before load
4. real authenticated API principals are created through the gateway
5. warmup completes and the Redis users-list cache is materialized
6. the configured multi-principal concurrent burst request set completes
7. the multi-principal sustained profile completes
8. policy-compliant profile error rates remain within the hosted guardrail
9. policy-compliant profile p95 latencies remain within the hosted guardrail
10. policy-compliant profile throughput remains above the hosted floor
11. p50/p95/p99/max latency observations are recorded
12. gateway health stays responsive while sustained load runs
13. a deliberate single-principal overload is rejected with HTTP 429 while initial requests are admitted
14. core services are healthy after load
15. a protected read succeeds after load
16. read-only state remains unchanged after benchmark setup
17. the report retains the hosted-only truth boundary

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

The checked-in `performance-load-runtime-summary.json` deliberately contains no fabricated benchmark numbers. Measurements only become evidence when the hosted workflow actually runs.

## Truth boundary

Evidence class: `HOSTED_RUNTIME`.

A passing workflow means the checked Aetheris revision satisfied the declared regression guardrails and rate-limit assertions in that specific GitHub-hosted Docker Compose run. It does **not** prove:

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
