# Core Runtime Proof

This document describes the hosted runtime verification layer for the Aetheris core platform.

The purpose is to turn the strongest portfolio claims into observed behavior inside a clean GitHub-hosted Docker Compose environment. This is stronger than static source inspection, but it is deliberately narrower than production or physical-PC validation.

## Evidence class

`HOSTED_RUNTIME`

This evidence class means the behavior was exercised by GitHub Actions on an Ubuntu runner using the repository's Docker Compose stack.

It does **not** mean:

- the system is production-ready;
- the owner target PC has been validated;
- GPU/local-model/browser/operator behavior has been validated;
- external users or real production traffic have exercised the system;
- every failure mode has been exhausted.

Physical-machine status for the later Syntra/Aetheris track remains `BLOCKED_PENDING_HARDWARE`.

## What the workflow proves

The `Core Runtime Proof` workflow boots PostgreSQL, Redis, RabbitMQ, the user service, identity service, audit service, orchestrator service and gateway, then runs a deterministic smoke harness against the running stack.

The runtime contract covers:

1. PostgreSQL, Redis and RabbitMQ readiness;
2. identity, user, audit and gateway health;
3. registration through the gateway;
4. protected-route rejection when no bearer token is supplied;
5. a newly registered `API_CONSUMER` denied access to global User data;
6. a separately registered synthetic account promoted to `ADMIN` only in disposable PostgreSQL, then logged in through Identity for real signed, authorized User reads;
7. rejection of `API_CONSUMER` writes;
7. refresh-token rotation;
8. rejection of a previously used refresh token;
9. refresh-token revocation through logout;
10. actual Redis cache materialization for `userById` and `usersList`;
11. a real `user.created` event travelling through RabbitMQ, being persisted by the audit service, surviving an audit-service restart, and remaining queryable afterward;
12. gateway fallback when `user-service` is deliberately stopped;
13. route recovery after `user-service` is restarted.

The audit service stores consumed domain events idempotently in PostgreSQL using `event_id` as the conflict boundary. `GET /api/events` intentionally exposes only the newest 100 events, but that response limit does not delete older persisted audit rows.

The machine-readable contract is stored at:

- `build-evidence/runtime/core-runtime-contract.json`

The executable harness is:

- `tools/core_runtime_smoke.py`

The CI workflow is:

- `.github/workflows/core-runtime-proof.yml`

## Why the RabbitMQ probe uses the internal service port

Self-registration creates an `API_CONSUMER` identity, which is **not** authorized to read or write global User records in the review branch. The runtime proof first verifies that consumer reads and writes are denied. A second synthetic account is promoted in the **disposable test database only**, then signs in through the actual Identity service to obtain a signed `ADMIN` token.

The proof uses that token for User creation and cache reads, direct Audit event retrieval, and Gateway fallback/recovery. It must never interpret local-port reachability or forged identity headers as authorization. The full production security rollout remains review-only until independent acceptance.

## Failure behavior

If any required observation fails, the job fails closed. The harness writes a partial `FAIL` report showing which checks had already passed, and the workflow captures Compose state plus bounded failure logs for diagnosis.

A green workflow is therefore positive evidence for the exact commit that ran. A README statement by itself is not treated as proof.

## Relationship to repository evidence

The repository also contains static evidence in `build-evidence/portfolio/core-platform-evidence.json` and the recruiter-facing index in `docs/portfolio-evidence.md`.

The evidence layers should be interpreted as:

- **source/config evidence** — the design and implementation exist in the repository;
- **hosted runtime evidence** — selected core behavior actually runs in CI;
- **physical-machine evidence** — still pending for features that depend on the owner's future workstation.

This distinction is intentional and should remain visible in recruiter/interview discussions.
