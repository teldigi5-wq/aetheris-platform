# Resilience Runtime Proof

This post-roadmap capability pass adds **hosted runtime evidence** for the gateway resilience behavior already defined in the Aetheris core platform.

It is not a new numbered roadmap stage. The historical Syntra × Aetheris roadmap remains complete at **Stage 34 / 34**, and there is still **no Stage 35**.

## What this proof exercises

The workflow starts the relevant Docker Compose services on a GitHub-hosted Ubuntu runner and drives a real user-service outage through the running gateway.

The proof verifies that:

1. gateway, identity-service and user-service are healthy before the exercise;
2. an authenticated `/api/users` read succeeds through the gateway;
3. `userReadCircuit` begins in `CLOSED` state;
4. stopping `user-service` produces the configured structured HTTP `503` fallback;
5. repeated failures drive `userReadCircuit` to `OPEN`;
6. an additional request is rejected by the open circuit and increments `notPermittedCalls`;
7. the gateway actuator control plane remains responsive while the downstream service is unavailable;
8. `user-service` can restart and become healthy again;
9. after the configured open-state wait, the circuit transitions to `HALF_OPEN` without restarting the gateway;
10. three successful half-open probes close the circuit;
11. the protected user route returns HTTP `200` again after recovery.

The machine-readable contract contains **13 checks** because readiness and state-transition evidence are recorded separately.

## Files

- `.github/workflows/resilience-runtime-proof.yml`
- `tools/resilience_runtime_smoke.py`
- `build-evidence/runtime/resilience-runtime-contract.json`
- generated workflow artifact: `build-evidence/runtime/resilience-runtime-report.json`

The workflow also captures Compose state, the gateway circuit-breaker snapshot and circuit-breaker events for review.

## Why this is stronger than a configuration-only claim

`docs/resilience.md` explains the intended policy: bounded read retries, circuit breaking, structured fallbacks and recovery. This proof does not merely parse that configuration. It boots the Compose stack, removes the downstream service, observes the live actuator state, drives the circuit open, restores the service and verifies the circuit closes again.

The liveness assertion deliberately uses the gateway actuator control plane instead of requiring aggregate `/actuator/health` to remain `UP` during an intentionally open circuit. That keeps the proof compatible with health-indicator policies that may correctly report a degraded dependency while the gateway process itself remains responsive.

That makes the resilience story easier to defend in interviews while keeping the claim narrow and reproducible.

## Truth boundary

Evidence class: `HOSTED_RUNTIME`  
Capability: `resilience`  
Environment: GitHub-hosted Ubuntu + Docker Compose  
Physical-PC validation: `false`

This does **not** prove production readiness, long-duration reliability, traffic-scale behavior, formal chaos-engineering maturity, Kubernetes failure recovery, or target-PC behavior.

The physical status therefore remains:

`BLOCKED_PENDING_HARDWARE`

Hosted CI remains repository evidence and is not a substitute for validation on the owner's future physical machine.
