# Phase 12 — Specialist Divisions Production Hardening

Phase 12 follows the certified Phase 11 security hardening pass and begins the master-build-spec item **remaining specialist divisions**.

This phase does not add catalog-only personas. Each slice closes a concrete runtime-governance gap in the specialist organization that already exists.

## Slice 1 — Specialist tool authority

Aetheris already defines each specialist with an `allowed-tools` list, and the safe-tool registry already requires a known agent. Before Phase 12, however, the registry did not enforce the specialist's declared tool families. Any known agent could therefore request any registered safe tool and rely only on the ordinary owner policy, risk and approval layers.

That made `allowed-tools` descriptive metadata rather than an execution boundary.

`SpecialistToolAuthorizationService` now sits in front of ordinary safe-tool policy evaluation.

Current safe-tool families are explicit and finite:

| Registered tool | Specialist family |
| --- | --- |
| `github.read` | `github` |
| `github.propose-change` | `github` |
| `files.read-workspace` | `filesystem` |
| `files.write-workspace` | `filesystem` |
| `terminal.inspect` | `terminal` |
| `terminal.execute-workspace` | `terminal` |

For a safe-tool request, the runtime resolves the registered tool and known specialist, requires an explicit tool-family mapping and exact catalog family grant, then evaluates ordinary owner policy, mode, risk and approval requirements. A specialist deny cannot be overridden by owner approval.

## Slice 2 — Independent specialist verification and governed completion

The generic task state machine previously allowed `VERIFYING -> COMPLETED` when a caller supplied any `agentId`. Engineering workflows happened to use a separate Software Architect, but that independence was a workflow convention rather than a platform completion rule.

`TaskVerificationService` now creates a reusable governed-completion boundary using the existing durable task-event journal and agent catalog. It adds no new persistence table or public endpoint.

A task can complete from `VERIFYING` only when all of the following are true:

1. a known non-executive specialist is recorded while the task is `RUNNING`;
2. a distinct known QA/review specialist performs the `RUNNING -> VERIFYING` handoff;
3. a third known specialist records the final verification decision;
4. the final verifier is different from both executor and QA/reviewer;
5. the final verifier has at least one explicit verification-oriented catalog capability such as `code-review`, `integration-testing`, `release-gates`, `architecture-review`, `secure-code-review`, `source-verification`, `remediation-verification` or `evaluation`;
6. the decision is a recorded `PASS` for the current executor/reviewer context;
7. the completing `agentId` is the same specialist that recorded that passing decision.

The verification event records only governance metadata: decision, evidence type, executor id, reviewer id and the verifier identity already present on the task event. Existing engineering evidence remains in `aetheris_verification_evidence`; the task-level decision does not replace substantive QA evidence.

### Fail-closed behavior

- direct API completion without a recorded decision is denied;
- the execution specialist cannot self-verify;
- the QA/review handoff specialist cannot self-promote to final verifier;
- an unknown verifier is denied by the existing agent catalog;
- a known specialist without a verification-oriented capability is denied;
- a failed verification decision cannot complete a task;
- owner approval permits an approved action but cannot become verification evidence or bypass completion governance;
- stale/mismatched decisions are rejected because the recorded executor/reviewer context must match the current task history.

`EngineeringWorkflowService` now records its Software Architect decision through this shared boundary before task completion. Both the state-only Stage 3 workflow path and the adapter-backed evidence path remain governed by the same final completion rule.

### Connector approved-action compatibility

The full PR runtime suite exposed an existing connector lifecycle that used `automation-engineer` as executor, review handoff and final completer. The new boundary correctly rejected that self-certifying path during the Connector Approved Actions Phase 6 proof.

The connector lifecycle is now migrated rather than exempted:

- `automation-engineer` executes the approved connector action and records the provider receipt;
- `qa-engineer` performs the `RUNNING -> VERIFYING` review handoff and reviews the receipt;
- `mcp-integration-engineer` records the final independent `CONNECTOR_RECEIPT` decision using its existing `integration-testing` capability and owns completion.

No connector-specific bypass was added. Synthetic and enabled live-write paths therefore use the same generic independent-verification boundary as other governed specialist work. The Phase 12 proof contains a source guard that rejects any regression back to `automation-engineer` self-completion, while the existing Connector Approved Actions runtime proof exercises the behavior end to end.

## Slice 3 — Governed specialist delegation

The catalog has long declared a `delegation` capability for the Executive Planner, but task assignment itself previously accepted a different `agentId` when moving into execution without checking whether the current specialist was allowed to delegate. Mission plans also persisted their requested `agentId` without enforcing that the identity existed in the current specialist catalog.

`TaskDelegationService` now creates the reusable delegation boundary.

A change of execution specialist during `PLANNING/AWAITING_APPROVAL -> RUNNING` is treated as a delegation and succeeds only when:

1. the current task agent is a known catalog specialist;
2. the requested execution specialist is also a known catalog specialist;
3. the delegator and delegate are different identities;
4. the delegator has the exact catalog capability `delegation`;
5. the task itself is reused, so its `OperationMode`, audit history and independent-verification requirement are not replaced by a less restrictive child task.

The current catalog therefore makes `executive-planner` the explicit coordinator allowed to hand execution to another specialist. Ordinary specialists cannot chain work into a more privileged role merely by supplying a different `agentId`.

### No authority transfer

Delegation is assignment, not privilege inheritance.

The delegation event records:

- `delegationDecision=ALLOW`;
- `delegationStage=PLANNED|ACTIVATED`;
- delegator and delegate IDs;
- delegate division and risk class;
- unchanged task mode;
- `authorityTransferred=false`;
- `verificationRequired=true`;
- the delegate's catalog tool/capability snapshot for audit context.

The delegate still passes through Slice 1 using only its own exact `allowed-tools` families. A delegated Research Scientist therefore does not gain GitHub or terminal authority just because the Executive Planner assigned the task. `PRIVATE` and `ZERO_COST` task modes remain attached to the same task and continue to constrain downstream owner-policy evaluation.

### Assignment boundary rather than every handoff

Only a change of specialist when a task enters execution is classified as delegation. The following remain their existing concepts instead of being incorrectly reclassified:

- a specialist continuing its own task from planning into execution;
- owner pause/resume and emergency controls;
- approval pause/resume under the same active agent;
- `RUNNING -> VERIFYING` QA/review handoff;
- final independent verification.

This keeps delegation governance narrow and prevents accidental interference with recovery and approval state machines.

### Mission-plan hardening

`MissionPlannerService` now validates every materialized step against the same delegation boundary before creating tasks. Blank or invented specialist IDs fail closed.

For valid mission steps it:

1. records a durable `PLANNED` delegation event on the new task;
2. keeps `executive-planner` as the task's planning authority when dependencies release;
3. preserves the requested specialist as the delegate on the mission-plan node;
4. embeds `delegatorId`, `delegateId` and `authorityTransferred=false` in the durable work-item payload so scheduler evidence retains delegation lineage.

An old Stage 7 fixture used the nonexistent `research-director` identity. The stricter boundary exposed that stale fixture, which is now corrected to the real `research-scientist` catalog identity rather than receiving a compatibility bypass.

## Phase 12 acceptance proof

`Phase12SpecialistDivisionsIntegrationTest` proves specialist tool-family isolation and approval precedence.

`Phase12SpecialistVerificationIntegrationTest` proves:

1. completion without a verification decision fails closed;
2. executor self-verification is denied;
3. QA/reviewer self-promotion is denied;
4. a known non-verifier specialist is denied;
5. an invented verifier identity is denied;
6. owner approval cannot bypass verification;
7. a failed verifier decision cannot complete the task;
8. a valid independent decision permits completion and is present in the durable task history;
9. the existing engineering workflow completes through the governed boundary.

`Phase12GovernedDelegationIntegrationTest` proves:

1. Executive Planner delegation succeeds and is durably audited;
2. delegation cannot expand the delegate's tool families;
3. the parent `PRIVATE` mode survives delegation and still blocks off-device policy access;
4. a specialist without `delegation` capability cannot assign another specialist;
5. an invented delegate identity is denied;
6. self-delegation is denied;
7. mission planning rejects an unknown specialist before task materialization;
8. a valid mission records planned delegation lineage and retains the Executive Planner as planning authority.

The dedicated **Phase 12 Specialist Divisions Proof** workflow runs all three integration suites plus source guards on the feature branch, pull request and canonical development branch. The repository's Connector Approved Actions Phase 6 Runtime Proof remains an independent runtime compatibility gate for the connector write lifecycle.

## Contract and migration impact

These slices intentionally add no:

- agent IDs;
- HTTP endpoints;
- JPA tables;
- external credentials;
- tool execution adapters;
- remote-control surfaces;
- live-money capability.

Verification and delegation decisions reuse the existing task-event journal. Mission work-item delegation lineage reuses the existing payload field. The frozen Stage 23 public/runtime compatibility surface should therefore remain unchanged and must be confirmed by ordinary CI before merge.

## Security review

Phase 12 now narrows authority at three independent layers:

`governed delegation -> specialist tool boundary -> owner policy/mode/risk -> approval -> emergency/runtime controls -> execution/sandbox -> QA/review -> independent verifier -> governed completion`

Delegation cannot manufacture a new identity, give a specialist another role's tools, replace the task's policy mode, or remove the downstream verifier requirement. Neither owner approval nor a caller-supplied `agentId` can expand specialist tool authority or manufacture a completion decision. Existing connector actions are migrated through the same separation-of-duties boundary rather than receiving an exemption.

## Rollback

Rollback remains code-only. No persistence migration or credential movement is involved.

If Slice 3 must be rolled back for compatibility, revert the delegation service, task-assignment hook, mission planner changes, corrected fixture, proof tests/workflow and documentation as one unit. Do not leave mission payloads claiming governed delegation if the runtime assignment guard has been removed.

## Next Phase 12 slices

After execution authority, completion independence and delegation are certified, the next audit should inspect scheduler/worker identity binding and any remaining specialist execution surfaces before deciding whether Phase 12 is complete.
