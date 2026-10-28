# Signed principal role-scope ceilings in direct User and Audit filters

**28 October 2026 · stacked on draft #341 · Stage45 NO_GO**

## Actual security gap

Gateway's real reactive JWT boundary already verifies that asserted JWT
scopes are a subset of a known Identity role's authorized scopes, and
rejects signed principals with malformed subjects. User and Audit direct
OncePerRequestFilter implementations previously validated token signatures
and route-specific scope presence, but did **not** verify the complete
role-to-scope ceiling nor require trimmed subjects.

A signed-but-misissued or internally manipulated ADMIN/DEVELOPER/
API_CONSUMER token with extra asserted permissions could therefore pass
some independent backend checks, even though Gateway would reject it.
A direct caller with a signed token and access to a resource-server port
must be assessed by the resource server itself.

## This code change

Both User and Audit now enforce matching copies of the Identity and
Gateway role ceiling for ADMIN, DEVELOPER and API_CONSUMER **after**
signature validation and **before** route authorization or MVC controller
execution. They also require a bounded nonblank trimmed subject. Scope
collections are already type-checked, nonempty and bounded; this PR
preserves those checks and returns HTTP 401 for invalid signed principal
claims. Valid scope-bearing consumers still get 403 for privileged-only
API paths. No new roles, scopes, endpoint, tenant ID, workload privilege
or object ownership grant is introduced.

Actual servlet MockMvc tests in both modules sign throwaway HMAC and
RS256 JWTs, prove that forbidden role+scope combinations and malformed
subjects never reach the data service, and prove a valid ADMIN
request still reaches the intended controller. Exact-head CI runs each
new test class explicitly, plus the full four-module reactor, real
Identity-to-resource RS256 token interop, synthetic PostgreSQL 16
isolation/migrations and Stage45 NO_GO gate.

## Hard blockers not resolved by this PR

The policy is duplicated between Identity/Gateway/User/Audit and must
be kept in sync or placed in a separately reviewed shared auth contract
before broader authorization changes. In particular, the existing
API_CONSUMER direct User-object grant remains DENIED: Identity.uid
and User.id are unrelated sequences, and unsigned caller-supplied
tenant headers are not ownership evidence.

No actual client-mTLS identity, token exchange, organization membership,
user-to-resource ownership database link, external revocation,
production secret rollout, cluster, physical device, protected-main
merge or deployment was exercised. This is defense-in-depth on the
actual running Java filter code under synthetic authentication tests,
not external security certification.

**Issue #251 OPEN · Nine release attestations outstanding · NO_GO.**
