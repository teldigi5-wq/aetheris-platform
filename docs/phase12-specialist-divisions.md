# Phase 12 — Specialist Divisions Production Hardening

Phase 12 follows the certified Phase 11 security hardening pass and implements the master-build-spec item **remaining specialist divisions** as concrete runtime-governance slices rather than catalog-only personas.

## Slice 1 — Specialist tool authority

Aetheris already declared `allowed-tools` per specialist, but before Slice 1 those declarations were descriptive metadata. `SpecialistToolAuthorizationService` now sits in front of ordinary safe-tool policy evaluation and requires an exact registered tool-family grant for the active specialist.

Current safe-tool families are explicit and finite:

| Registered tool | Specialist family |
| --- | --- |
| `github.read` | `github` |
| `github.propose-change` | `github` |
| `files.read-workspace` | `filesystem` |
| `files.write-workspace` | `filesystem` |
| `terminal.inspect` | `terminal` |
| `terminal.execute-workspace` | `terminal` |

A specialist deny cannot be overridden by owner approval. Ordinary owner policy, mode, risk, approval, emergency and runtime controls still apply after specialist authorization succeeds.

## Slice 2 — Independent specialist verification and governed completion

The generic task state machine previously allowed `VERIFYING -> COMPLETED` from a caller-supplied `agentId`. `TaskVerificationService` now makes completion a reusable governance boundary.

A task can complete from `VERIFYING` only when:

1. a known non-executive specialist was recorded as executor while the task was `RUNNING`;
2. a distinct known QA/review specialist performed the `RUNNING -> VERIFYING` handoff;
3. a third known specialist recorded the final verification decision;
4. that verifier differs from both executor and reviewer;
5. the verifier has an explicit verification-oriented catalog capability;
6. the recorded decision is `PASS` for the current executor/reviewer context; and
7. the completing `agentId` is the same specialist that recorded the passing decision.

Owner approval can authorize an approved action but cannot become verification evidence. Connector approved-action execution was migrated to the same generic rule: `automation-engineer` executes, `qa-engineer` reviews, and `mcp-integration-engineer` independently verifies and completes.

## Slice 3 — Governed specialist delegation

`TaskDelegationService` turns specialist assignment into an explicit authority boundary. A change of execution specialist during `PLANNING/AWAITING_APPROVAL -> RUNNING` is a delegation and succeeds only when the delegator and delegate are known, distinct catalog identities and the delegator has the exact `delegation` capability.

The current catalog therefore makes `executive-planner` the coordinator allowed to assign another specialist. Delegation is assignment, not privilege inheritance:

- the same task and `OperationMode` are retained;
- the delegate receives only its own catalog capabilities and tool families;
- `authorityTransferred=false` is recorded in durable delegation evidence;
- `verificationRequired=true` remains attached to the downstream lifecycle;
- owner approval cannot substitute for delegation authority.

Mission plans validate every specialist before materialization, record planned delegation lineage, keep `executive-planner` as planning authority, and carry delegator/delegate lineage into durable work-item payloads. Approval-resumed engineering work explicitly activates the governed `executive-planner -> backend-engineer` delegation before backend execution is recorded.

## Slice 4 — Scheduler/worker specialist identity binding

The scheduler audit found a separate execution-identity gap after delegation was already governed: worker heartbeats, scheduler dispatch and direct queue claims were keyed by arbitrary `workerId` strings. Stage 9 capability routing also selected workers by those strings. A worker could therefore be capability-compatible without proving that it represented the specialist assigned to the work item.

Slice 4 closes that gap at the lease boundary.

### Worker binding

`WorkerHeartbeatRequest` now accepts an optional `agentId`. When supplied:

1. the agent must exist in the canonical `AgentCatalogService`;
2. the heartbeat row records that specialist identity;
3. a worker may bind once from unbound to a known specialist;
4. a bound worker cannot later rebind to another specialist by sending a new heartbeat.

Legacy infrastructure workers may remain unbound, but they are eligible only for work that has no specialist requirement.

This is an application-level catalog identity binding. It does **not** claim cryptographic machine attestation. Network/API authentication and any future host attestation remain separate security layers.

### Work-item binding

`WorkItemEntity` now carries nullable `requiredAgentId` authority metadata.

- ordinary enqueue derives the required specialist from the task's current active agent only when that identity is a known catalog specialist;
- generic/unassigned infrastructure work remains compatible with legacy workers;
- `enqueueForSpecialist(...)` creates an explicit, validated specialist requirement for cases where the execution specialist intentionally differs from the task's current planning authority.

Mission release uses `enqueueForSpecialist(...)` with the mission node's validated delegate. A mission task can therefore correctly remain under `executive-planner` during planning while the queued execution lease is restricted to the delegated `research-scientist`, `backend-engineer`, or other validated specialist.

### One claim boundary

`WorkerIdentityBindingService` is the shared fail-closed boundary for work claims.

For specialist-bound work, a claim succeeds only when:

1. the work item has an exact known `requiredAgentId`;
2. the supplied `workerId` has a current heartbeat row;
3. that heartbeat is bound to the same exact specialist identity.

The rule is enforced by:

- direct `DurableWorkQueueService.claim(...)` calls;
- lease renewal;
- ordinary `SchedulerService.dispatch(...)`;
- `CapabilityAwareDispatchService` Stage 9 routing.

A capability declaration therefore cannot override specialist identity. A research-bound worker advertising `coding` cannot claim backend-engineer work merely because its Stage 9 capability set matches.

### Compatibility behavior

Work items with no known specialist assignment retain the legacy generic-worker path. This preserves Stage 7/Stage 9 infrastructure scheduling behavior while making specialist execution fail closed.

The scheduler skips ready tickets whose required specialist does not match the requesting worker instead of turning an identity mismatch into a lease. Direct claim attempts fail with an explicit identity-binding error and leave the item queued.

## Phase 12 acceptance proof

The dedicated **Phase 12 Specialist Divisions Proof** workflow runs four integration suites:

- `Phase12SpecialistDivisionsIntegrationTest` — exact specialist tool-family isolation and approval precedence;
- `Phase12SpecialistVerificationIntegrationTest` — independent verification and governed completion;
- `Phase12GovernedDelegationIntegrationTest` — delegation authority, no privilege transfer, mission lineage and approval-resume activation;
- `Phase12WorkerIdentityBindingIntegrationTest` — scheduler/worker execution identity.

The Slice 4 suite proves:

1. a worker heartbeat cannot bind to an invented specialist;
2. a worker's specialist binding cannot be changed by a later heartbeat;
3. direct queue claims cannot cross specialist identity;
4. mission work is lease-bound to the validated delegate while the task retains planner authority during planning;
5. Stage 9 capability matching cannot override specialist identity;
6. generic work with no specialist requirement remains compatible with an unbound legacy worker.

The workflow also contains source guards proving the worker binding is used by the direct queue, ordinary scheduler, capability-aware scheduler and mission enqueue path, with no wildcard specialist grant.

Repository-wide Build, contract-freeze, Stage 26, CodeQL, connector proofs, hosted runtime proofs and reproducibility remain independent merge gates.

## Contract and migration impact

Slices 1–3 intentionally added no endpoint, table, credential or execution-adapter surface.

Slice 4 still adds no endpoint, table, agent ID, credential, remote-control surface or execution adapter, but it adds two nullable persistence fields to existing entities:

- `aetheris_worker_heartbeats.agent_id`;
- `aetheris_work_items.required_agent_id`.

It also additively exposes optional specialist identity in the existing worker-heartbeat/work-item JSON contracts. Existing three-argument Java heartbeat construction remains supported for generic tests and infrastructure workers.

The Stage 23 frozen endpoint/table/agent surface is expected to remain unchanged; ordinary CI must still confirm that before merge. Any environment using explicit schema migrations rather than repository test/create-update behavior must add equivalent nullable columns before activating Slice 4.

## Security review

Phase 12 now narrows authority through this chain:

`governed delegation -> specialist-bound work item -> specialist-bound worker lease -> specialist tool boundary -> owner policy/mode/risk -> approval -> emergency/runtime controls -> execution/sandbox -> QA/review -> independent verifier -> governed completion`

A worker cannot change its persisted specialist binding, an arbitrary worker ID cannot claim specialist-bound work, and a matching Stage 9 capability set cannot substitute for the required specialist identity. Delegation still cannot manufacture a new identity, transfer tools, replace the task mode or remove verification.

The worker binding is deliberately not described as cryptographic attestation. A future trusted-host/device identity layer may strengthen who is allowed to assert a worker identity, but that is outside this repository-side Slice 4 claim.

## Rollback

Slice 4 is rollback-safe at application level because both new persistence fields are nullable and existing generic work remains valid.

If Slice 4 must be rolled back, revert the worker binding service, heartbeat identity field/request extension, work-item required-agent field, queue/scheduler/capability-dispatch enforcement, mission specialist enqueue hook, Slice 4 tests/workflow guard and this documentation as one unit. Existing nullable columns may remain harmlessly unused until a migration cleanup is deliberately scheduled.

## Next Phase 12 audit

With tool authority, independent completion, governed delegation and scheduler/worker identity binding covered, the next audit should inspect **remaining specialist execution surfaces that bypass the durable work-queue/scheduler path**. Phase 12 should be declared complete only if every such consequential execution surface already consumes the catalog specialist boundary, owner policy/mode/risk controls and independent verification where applicable; otherwise the next slice should close the concrete gap rather than add new personas.
