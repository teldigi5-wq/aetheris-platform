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
5. successful authorized user reads;
6. scope denial for an `API_CONSUMER` attempting `users:write`;
7. refresh-token rotation;
8. rejection of a previously used refresh token;
9. refresh-token revocation through logout;
10. actual Redis cache materialization for `userById` and `usersList`;
11. a real `user.created` event travelling through RabbitMQ and appearing in the audit service;
12. gateway fallback when `user-service` is deliberately stopped;
13. route recovery after `user-service` is restarted.

The machine-readable contract is stored at:

- `build-evidence/runtime/core-runtime-contract.json`

The executable harness is:

- `tools/core_runtime_smoke.py`

The CI workflow is:

- `.github/workflows/core-runtime-proof.yml`

## Why the RabbitMQ probe uses the internal service port

Self-registration creates an `API_CONSUMER` identity. That role intentionally has `users:read` but not `users:write`.

The runtime proof first verifies that a write through the public gateway is rejected with `403`. It then calls the local user-service port directly inside the test environment to create a user specifically for the internal messaging probe. That user creation publishes `user.created`, which the audit service must observe through RabbitMQ.

This separation demonstrates both boundaries:

- the gateway enforces the external authorization policy;
- the internal asynchronous event path works when the service operation is legitimately invoked.

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
