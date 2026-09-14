# Stage 15 Owner Policy — Recovery Execution & Chaos Validation

This policy defines the owner-control boundary for Stage 15.

## 1. Owner remains final authority

A model, agent, incident classifier, reliability score or chaos rehearsal cannot grant execution authority. High-impact recovery must remain task-bound, approval-bound and auditable.

## 2. Exact approval binding

Recovery authorization requires:

- an existing recovery plan;
- approval status `APPROVED`;
- action type exactly `STAGE15_RECOVERY_EXECUTE`;
- approval task id equal to recovery-plan task id.

An approval for another task/action is invalid.

## 3. Bounded actions only

Allowed catalog actions:

`RESTART_SERVICE`, `PAUSE_PROVIDER`, `REVOKE_REMOTE_SESSION`, `ROLLBACK_RELEASE`, `PAUSE_TASK`, `STOP_TRADING`, `FAILOVER_READ_ONLY_PROVIDER`, `CLEAR_BOUNDED_CACHE`.

Always forbidden in Stage 15:

`ARBITRARY_SHELL`, `ADMIN_BYPASS`, `LIVE_ORDER`, `WITHDRAWAL`, `TRANSFER`.

Unknown actions fail closed.

## 4. Current execution adapter is evidence-only

Until a physical target/provider adapter has been deliberately configured and validated, Stage 15 must not mutate a real workstation, provider, tunnel, application, exchange or deployment.

Owner approval alone is insufficient to claim execution. The current state after approval is `AUTHORIZED_PENDING_TARGET_EVIDENCE`.

## 5. Error budgets and maintenance

Measured reliability evidence controls execution readiness. An exhausted error budget or active maintenance window blocks automated execution readiness. The owner may still investigate or take manual action outside this automation fabric.

## 6. Chaos is simulation-only

Repository/CI Stage 15 chaos testing is restricted to isolated dependency/failure simulation. It must never inject faults into a real production or owner workstation environment without a later explicitly authorized implementation designed for that environment.

## 7. Evidence cannot be invented

Execution, health, recovery and notification delivery claims require their stated external/target/provider evidence. CI fixtures are tests of the evidence pipeline; they are not production proof.

## 8. Verify after recovery

Recovery is not successful merely because an action was approved or reported. Target-measured post-action verification is required.

- Healthy verification may become `VERIFIED_RECOVERED`.
- Failed verification becomes `VERIFY_FAILED_ROLLBACK_REQUIRED`.
- Missing evidence must not become a success claim.

## 9. Rollback is governed

A rollback recommendation is not permission to mutate the target. Rollback must remain a catalog-bounded action under the same policy/approval/evidence controls.

## 10. Escalation receipts are external evidence

Stage 15 may record provider-measured notification receipts, but it must not infer delivery from a queued/requested notification. Missing receipt evidence remains unknown.

## 11. Incident replay is read-only

Replay reconstructs evidence and state transitions. It must never replay execution side effects.

## 12. Financial controls remain stronger than recovery logic

- no live orders;
- no withdrawals;
- no transfers;
- `STOP_TRADING` may reduce authority/risk, never increase it;
- the deterministic trading Risk Officer remains authoritative.

## 13. Emergency owner controls outrank automation

`STOP ALL`, `PAUSE`, and `TAKE CONTROL` must interrupt or outrank model routing, agents, recovery planning and background jobs whenever technically applicable.

## 14. External validation remains pending

The user does not yet have the target PC. Do not represent Windows, GPU, microphone, DPAPI, local-model, desktop automation or physical recovery behavior as validated until it is actually measured on the owner's target environment.
