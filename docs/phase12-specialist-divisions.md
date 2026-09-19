# Phase 12 — Specialist Divisions Production Hardening

Phase 12 follows the certified Phase 11 security hardening pass and begins the master-build-spec item **remaining specialist divisions**.

This first production slice does not add catalog-only personas. It closes a real execution gap in the specialist runtime that already exists.

## Gap closed

Aetheris already defines each specialist with an `allowed-tools` list, and the safe-tool registry already requires a known agent. Before Phase 12, however, the registry did not enforce the specialist's declared tool families. Any known agent could therefore request any registered safe tool and rely only on the ordinary owner policy, risk and approval layers.

That made `allowed-tools` descriptive metadata rather than an execution boundary.

## New specialist tool boundary

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

The specialist catalog already uses these family names in agent `allowed-tools` declarations.

## Enforcement order

For a safe-tool request, the runtime now follows:

1. resolve the registered safe tool;
2. resolve the known specialist agent;
3. require an explicit tool-id-to-family mapping;
4. require that exact family in the specialist's configured `allowed-tools` list;
5. only then evaluate owner rules, operation mode, risk and ordinary approval requirements;
6. execution still requires the existing execution gateway, sandbox and audit path.

A specialist deny returns `allowed=false` and `requiresApproval=false`. Owner approval cannot expand the specialist's configured tool families.

## Fail-closed behavior

- Unknown agents remain rejected by `AgentCatalogService`.
- Unknown registered tool ids remain rejected by `SafeToolRegistryService`.
- A future tool descriptor with no explicit specialist-family mapping is denied.
- Matching is normalized but exact; there is no wildcard, prefix or fuzzy privilege expansion.
- A specialist family allow does not bypass owner rules, `PRIVATE`, `ZERO_COST`, risk classification, approvals, emergency stop, sandboxing or execution audit.

## Acceptance proof

`Phase12SpecialistDivisionsIntegrationTest` proves:

1. every current safe tool has an explicit specialist-family mapping;
2. a Backend Engineer can use its declared GitHub family and still passes ordinary policy;
3. an allowed HIGH-risk terminal tool still requires owner approval;
4. an owner-approved terminal action is still blocked for Professor/Tutor because that specialist does not declare the terminal family;
5. a LOW-risk GitHub read is blocked for Research Scientist because its declared families do not include GitHub;
6. a future unmapped tool fails closed even for Security Architect.

The dedicated **Phase 12 Specialist Divisions Proof** workflow runs this integration proof and source guards on the feature branch, pull request, and canonical development branch.

## Contract and migration impact

This slice intentionally adds no:

- agent IDs;
- HTTP endpoints;
- JPA tables;
- external credentials;
- tool execution adapters;
- remote-control surfaces;
- live-money capability.

The frozen Stage 23 public/runtime compatibility surface should therefore remain unchanged and must be confirmed by ordinary CI before merge.

## Security review

This change narrows authority. It does not grant a tool that was previously unavailable; instead, it prevents a known specialist from using a registered safe tool unless the agent catalog explicitly declares the matching family.

The hard precedence remains:

`specialist boundary → owner policy/mode/risk → approval → emergency/runtime controls → execution/sandbox → audit/verification`.

## Rollback

Rollback is code-only: revert the Phase 12 specialist-boundary commit. No persistence migration or credential movement is involved.

Rollback must not be used to silently restore cross-specialist access. If compatibility requires a specialist to use another family, update the reviewed catalog declaration and prove that change through CI instead.

## Next Phase 12 slices

Only after this execution boundary is certified should additional specialist roles or delegation capabilities be added. Any new agent ID must be justified by missing runtime capability, routed through the same policy/tool boundary, and independently verified rather than being added as catalog decoration.
