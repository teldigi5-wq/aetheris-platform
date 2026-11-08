# Gateway recovery reproducibility — 9 November 2026

Status: **review-only / issue #312 remains NO_GO**. This is an independent post-roadmap reliability investigation, not a new numbered stage.

## Verified evidence
- Protected main: `e357446613b0dcdb814d59b34192349f6925f606`; Node and NGINX/zlib source security fixes are already merged through PRs #407/#409.
- Run [42501817436](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42501817436), first attempt, artifact `13625932194`: 11/17 checks, gateway HTTP health remained unavailable for the existing 180-second window after controlled stop/start. Compose recorded gateway `Exited (143)`; other core containers stayed running. This does not identify the cause of failed restart.
- Same-run targeted attempt 2, artifact `13626311488`: 17/17 passed and persisted profile/count unchanged.
- Later protected-main push [run 42509961751](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42509961751) succeeded, including hosted rollback proof.

## Bounded next verification
1. On the *disposable hosted proof stack only*, sample gateway container state after stop, immediately after start, and when the health deadline succeeds or fails. Record only status, running flag, exit code, OOM flag, health state, and monotonic duration/attempt counts; never collect or publish full Docker inspect objects, environment variables, tokens or unredacted request payloads.
2. Require at least two independent stop/start cycles, same exact gateway image identity, authenticated access recovery and persisted state after every cycle. A failed cycle must fail CI; no automatic restart retries, silent gate bypass or increased timeout beyond the existing 180 seconds.
3. Add deterministic unit tests for state redaction, fail-closed deadline and failure evidence, plus machine-readable CI assertion of the cycle count. Keep the 17 existing release-contract checks unchanged.
4. Do not release solely on green hosted proof. Real gateway proxy/TLS, deployed image inventory, secrets/CA trust and stateful-service restoration require operator verification.

This document is a diagnostic checkpoint. It is **not** evidence that the proposed instrumentation or two-cycle proof has been implemented, run, merged, or deployed.
