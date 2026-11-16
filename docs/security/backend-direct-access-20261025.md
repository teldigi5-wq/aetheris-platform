# Direct User/Audit service JWT defense in depth — Issue #251, slice 1

**Status: DRAFT REVIEW / NOT PRODUCTION CERTIFIED — 25 October 2026.** Stacked on docs PR #266 and release candidate PR #264. No protected-main or real secrets/cluster changes.

## Verified weakness

`UserController` and `AuditController` accepted untrusted direct HTTP requests when their network ports were reachable without crossing Gateway. Compose loopback and Kubernetes ClusterIP/NetworkPolicy provide network isolation, not independently authenticated authorization. A caller on a local host/shared container/pod may have connectivity. This does **not** establish public internet exposure or exploitation.

## Defense-in-depth first implementation

- Independent JJWT HMAC verification in both services over the ORIGINAL client `Authorization: Bearer` header; ignore unsigned/spoofable `X-Aetheris-*` hints. Missing/malformed/expired tokens rejected with HTTP 401; signed insufficient role/scope rejected with 403.
- **ADMIN only** for User and Audit APIs until an authenticated User ↔ Identity Account linkage, tenant boundary and ownership model exist. User GET/HEAD need `users:read`, POST/DELETE/other methods need `users:write`; Audit GET/HEAD need `services:read`. No public write grant is added. This is intentionally stricter than the previous Gateway-only API_CONSUMER/DEVELOPER scopes, so a compatibility review is a mandatory release gate.
- Validate required subject, positive signed uid, issued-at and expiration claims and original signed role/scopes; require one bounded bearer token. JWT issuer/audience and service-to-service workload identity are **not yet implemented**; coordinate with independent identity/gateway PR #229 rather than adding conflicting claims.
- Secret delivered to User/Audit via existing required env variable on all three Compose variants and from the same Helm Kubernetes Secret as Identity/Gateway. No key material, generated certificates or real configuration values committed. A test-only key appears only in tests.
- Existing application health/liveness endpoints are unchanged. Swagger/OpenAPI exposure, actuator posture, CORS/CSRF and real network topology require independent separate verification.

## Verification and limitations

Dedicated MockMvc direct-route tests verify anonymous calls, fake `X-Aetheris-*` values, bad/expired JWTs, valid nonadmin and missing scopes, legitimate ADMIN access, and short-secret fail closed. Windows + Ubuntu workflow runs Maven tests for both services and configuration checks. **Do not claim tests passed unless CI on this exact PR head confirms**.

Remaining blockers for #251: issuer/audience and key rotation/trust store, signed server-to-server identities, proper distinct User resource IDs vs Identity uid, ownership/tenant isolation and admin policy signoff, regression of all actual Gateway/browser/mobile clients, anonymous/container direct HTTP probes in Compose and real CNI, token revocation/rotation and monitoring. Existing JWT refresh flows and per-action authorizations are not fully certified. The source-level filter is therefore **a partial remediation, not issue closure**.

## Release rules

Keep #264 and #266 ancestry intact. This proposed branch requires independent API compatibility review before any protected-main promotion; do not weaken 401/403 assertions, reuse dev JWTs as production credentials or bypass authorization to make integration tests pass. Preserve rollback by reverting just this stacked PR. Stage45 and nine release gates remain NO_GO.

## Follow-up hardening — positive *integral* JWT user IDs (draft)

Stacked on PR #267 without replacing its verified ADMIN-only User/Audit authentication. Both JWT filters previously evaluated `uid instanceof Number num && num.longValue() > 0`. That turns a signed fractional JSON number such as `42.5` into `42` and risks overflow for other numeric shapes. Actual Identity Account IDs are emitted as Java `Long` values and parsed as either `Integer` or `Long` for JSON whole-number inputs. This follow-up rejects all other numeric types, zero and negatives **before** checking signed role/scopes. It does not accept or compare an Identity Account ID to a User row ID; object ownership and tenancy remain unresolved.

Focused MockMvc regressions in **both** services now check: signed positive Long ID succeeds; signed fractional, zero and negative IDs produce **401**; duplicated `Authorization` headers produce **401** even if the tokens themselves are valid. User service additionally asserts direct `DELETE /api/users/7` returns **401** when anonymous or when sending unsigned Gateway-style role hints, **403** without a signed ADMIN + `users:write` permission, and **204** for an authorized signed ADMIN with the write scope. The preexisting filter policy, scope identifiers, token issuer, expected client compatibility and deployment secrets are otherwise unchanged.

This is a narrow token-claim correctness improvement, **not** a claim of JWT signer compromise or externally exploitable data breach. Do not pin HS256 in a service alone: the current Identity `JwtService.signWith(key)` chooses an HMAC algorithm based on key strength; any future issuer algorithm constraint must be coordinated with Identity, Gateway, both services and key rotation, rather than breaking accepted legitimate tokens.

**Remaining NO_GO:** independently signed issuer/audience, signed service workload identities, resource ownership/tenant isolation, direct-container/Pod probes, compatibility acceptance and full backend deployment rollouts. This stacked PR does not close issue #251, Stage45 or production certification. Check its **exact-head CI run** before asserting tests passed.
