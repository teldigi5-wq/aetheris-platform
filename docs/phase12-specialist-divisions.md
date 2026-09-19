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

The dedicated **Phase 12 Specialist Divisions Proof** workflow runs both integration suites plus source guards on the feature branch, pull request and canonical development branch. The repository's Connector Approved Actions Phase 6 Runtime Proof remains an independent runtime compatibility gate for the connector write lifecycle.

## Contract and migration impact

These slices intentionally add no:

- agent IDs;
- HTTP endpoints;
- JPA tables;
- external credentials;
- tool execution adapters;
- remote-control surfaces;
- live-money capability.

The task verification decision reuses the existing task-event journal. The frozen Stage 23 public/runtime compatibility surface should therefore remain unchanged and must be confirmed by ordinary CI before merge.

## Security review

Phase 12 narrows authority in two independent places:

`specialist tool boundary -> owner policy/mode/risk -> approval -> emergency/runtime controls -> execution/sandbox -> QA/review -> independent verifier -> governed completion`

Neither owner approval nor a caller-supplied `agentId` can expand specialist tool authority or manufacture a completion decision. Existing connector actions are migrated through the same separation-of-duties boundary rather than receiving an exemption.

## Rollback

Rollback is code-only. No persistence migration or credential movement is involved.

If Slice 2 must be rolled back for compatibility, revert the governed-completion changes as a unit so the task service, engineering workflow, connector action lifecycle, tests, workflow guard and documentation remain consistent. Do not selectively remove only the completion assertion while leaving verification events in place.

## Next Phase 12 slices

After both execution authority and completion independence are certified, the next audit should focus on governed delegation: which specialists may assign work to which other specialists, whether authority can increase through delegation, and whether delegated tasks preserve the parent task's policy and evidence requirements.
