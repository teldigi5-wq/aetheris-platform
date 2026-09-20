# Phase 12 Final Security & Authority Certification

## Status

This document defines **Phase 12 Slice 12 — Final Security & Authority Certification**.

The certification baseline is canonical development commit:

`bb2cf6b1d0aeb0882506faf3059d5b28395bec63`

That baseline contains the previously merged specialist-authority work plus Connector Account Continuity and OAuth Lifecycle Continuity. Slice 12 does not add a new product capability. It closes Phase 12 by proving that the security and authority controls remain coherent when exercised together.

## Certification scope

Phase 12 is considered complete only when the repository proves all of the following domains together:

- specialist division isolation and explicit tool authority;
- independent verification before governed completion;
- governed delegation without authority transfer or privilege invention;
- scheduler and worker identity binding;
- direct execution authority;
- browser execution authority evidence;
- GitHub publish authority;
- live connector write authority;
- safe-tool execution authority;
- stable provider-account continuity before live connector writes; and
- OAuth credential lifecycle continuity across completion, refresh and health checks.

The dedicated proof workflow `.github/workflows/phase12-closure-certification.yml` reruns the Phase 12 integration/authority suite as one consolidated certification gate while the existing specialist, provider-account-continuity and OAuth-lifecycle workflows continue to provide focused proofs.

## Consolidated executable proof

The final certification gate executes these test classes together:

1. `Phase12SpecialistDivisionsIntegrationTest`
2. `Phase12SpecialistVerificationIntegrationTest`
3. `Phase12GovernedDelegationIntegrationTest`
4. `Phase12WorkerIdentityBindingIntegrationTest`
5. `Phase12DirectExecutionAuthorityTest`
6. `Phase12GitHubPublishAuthorityTest`
7. `Phase12ConnectorLiveAuthorityTest`
8. `Phase12SafeToolExecutionAuthorityTest`
9. `Phase12ProviderAccountContinuityTest`
10. `Phase12OAuthLifecycleContinuityTest`

The gate also fails if a required Phase 12 proof document or focused proof workflow disappears.

## Exact-head acceptance gate

A pull request for this slice must not be merged unless all of the following are true for the **same exact PR head SHA**:

- every required check has completed;
- every required check is successful;
- the consolidated Phase 12 certification workflow is successful;
- the focused Phase 12 proof workflows are successful;
- there are no failed, cancelled, timed-out or still-running required checks; and
- no unresolved change weakens a fail-closed authority, continuity or verification boundary.

Passing checks from an older commit do not certify a newer head.

## Canonical merge certification

After the slice is merged, the resulting canonical development commit must be checked independently. Phase 12 is closed only when the required push-path workflows for that exact canonical merge SHA complete successfully.

The merge SHA is deliberately not hard-coded into this pre-merge document because it does not exist until GitHub performs the merge. The exact merge SHA and its workflow evidence are the post-merge certification record.

## Non-goals

Slice 12 intentionally does **not** introduce:

- local model inference;
- model-provider routing;
- RAG or structured memory changes;
- new public APIs;
- new database tables;
- new credential semantics;
- new connector providers;
- new agent identities; or
- Phase 13 functionality.

Those concerns belong to later phases and must not be mixed into the Phase 12 security closure gate.

## Gate to Phase 13

Phase 13 — **Syntra-Core** may begin only after:

1. this closure slice is merged;
2. the canonical merge SHA is fully green on the required workflow suite; and
3. the Phase 12 authority/security evidence is intact at that exact SHA.

This keeps the project progression evidence-based: **implement → prove exact head → merge → prove canonical merge SHA → advance phase**.
