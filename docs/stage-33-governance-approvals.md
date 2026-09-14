# Stage 33 — Governance, approvals and cross-system policy

Stage 33 implements a deterministic cross-system governance foundation around the existing Aetheris owner-policy, approval, reasoning, digital-twin and automation-control surfaces.

## Canonical lifecycle

Every governed action follows the same ordered lifecycle:

`UNDERSTAND → PLAN → CHECK RULES → ASSESS RISK → SIMULATE/PREVIEW → APPROVE WHEN REQUIRED → EXECUTE → VERIFY → RECORD → LEARN → REPORT`

The Stage 33 foundation enforces ordering and does not allow a caller to jump from planning directly to execution.

## Existing owner-rule integration

The existing `OwnerRuleService` / `OwnerRuleCompilerService` remains the canonical persisted owner-rule source. Stage 33 does **not** create a second owner-rule database or condition parser.

`ExistingOwnerRuleAdapter` projects only rule keys already reported as matched by the canonical compiler into Stage 33's package-private governance representation. It also detects structurally identical, equal-priority `ALLOW`/`DENY` conflicts in an affected scope and fails that scope closed. This keeps Stage 33 as a governance coordinator over the existing rules rather than a competing policy system.

The legacy PRIVATE-mode base denial can be deferred only to Stage 33's stricter exact-action/exact-scope private override gate; a matched owner `DENY` remains a hard block.

## Policy precedence

The effective safety order is intentionally conservative:

1. Stage 32 emergency control and platform hard blocks;
2. Zero-Cost and live-money hard boundaries;
3. existing structured owner `DENY` / `FORCE_ZERO_COST` / `FORCE_LOCAL` rules;
4. risk-driven simulation requirements;
5. owner approval requirements;
6. verification requirements;
7. ordinary ALLOW eligibility.

Conflicting ALLOW/BLOCK owner rules at the same priority fail closed. Model output is never treated as authority over a hard platform or owner policy boundary.

## Mode guarantees

- `ZERO_COST` hard-blocks a billable endpoint or any non-zero external cost. Approval cannot override that boundary.
- `PRIVATE` blocks protected private/secret context from leaving the device unless an explicit, action-scoped, scope-matched owner approval contains the private-mode override.
- Live-money execution remains blocked by default and is not introduced by Stage 33.

## Scoped approvals

The Stage 33 approval primitive binds an approval to an exact action ID and exact scope, with issue/expiry timestamps. One-time approvals are consumed once and replay is rejected. Approvals cannot silently widen their scope.

The existing database-backed task approval service remains intact; this Stage 33 primitive defines the stricter cross-system semantics that later integrations can adopt without changing current HTTP or persistence contracts.

## Risk, simulation and verification

Public, financial, destructive, privileged, irreversible and high/critical-risk effects receive stronger controls. Destructive, privileged, financial, irreversible and high-risk side-effect work requires a successful simulation/preview before the approval gate.

Preflight `ALLOW` means only that an action is eligible to execute. It is not evidence that execution happened or succeeded. If no execution observation exists, Stage 33 does not mark `EXECUTE` complete. Completion is `VERIFIED_SUCCESS` only when an execution observation is accompanied by a verified receipt with evidence references. Missing evidence yields the explicit `UNVERIFIED` state.

## Stage 32 integration

Any active Stage 32 emergency mode (`PAUSE`, `TAKE_CONTROL`, or `STOP`) blocks ordinary automated execution eligibility. `STOP` therefore cannot be bypassed by a model, owner ALLOW rule, or ordinary approval token.

## Truth boundary

This stage adds no unrestricted executor, privileged shell, production activation, destructive host mutation, new live-money authority, HTTP endpoint or database table. It does not claim physical-machine validation. Physical-machine status remains `BLOCKED_PENDING_HARDWARE` until real owner hardware is tested.

## Verification

```bash
mvn -B -f orchestrator-service/pom.xml -Dtest=Stage33GovernanceTest test
```

The dedicated workflow executes the Stage 33 suite twice to expose hidden state coupling. The canonical PR must also pass Build, previous-stage regressions, dependency lockdown/reproducibility and CodeQL before merge.
