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
