# S0-E6D — Runtime-audience Identity token compatibility candidate

**29 October 2026. Stage45 NO_GO. Draft only, no token route, no live customer rollout.**

## The actually verified mismatch

The certified platform `main` at `57ad3e0e602efa6feb275328254151b959a3499b` uses `identity-service/src/main/java/io/aetheris/identity/JwtService.java`: `Keys.hmacShaKeyFor` and `signWith(key)` to issue a shared-secret HS256 token with `sub, uid, name, role, scopes, iat, exp`. That service does NOT add signed `iss`, `aud`, or `kid`.

The proposed, unmerged Runtime S0-E6A/S0-E6B `RuntimePinnedPrincipalVerifier` at `teldigi5-wq/aetheris-ai-runtime@d7411b797557060e452a7a8ef34470d96a20268e` requires an operator-pinned **RS256** signature, exact trusted `kid`, exact `iss`, exactly one `aud`, numeric `uid`, allowed `role` and role-bounded `scopes`, valid `iat/exp`. Therefore the real existing Platform HS256 tokens **cannot** pass the newer Runtime verifier. Historic synthetic signer/verifier tests are not cross-repo interoperability evidence.

## Implemented independently on this Platform draft

`identity-service/src/main/java/io/aetheris/identity/RuntimeAudienceTokenIssuer.java` is a NEW Spring-created `@Component` that is **not** called by the current registration/login/refresh/controller flows, and adds **NO** publicly reachable endpoint. Unlike the existing legacy HS256 `JwtService`, this NEW optional issuer:

- Has NO default signing secret. All settings absent = no issue capability; partial/invalid operator settings = fail Spring startup closed. Operator must provision an RSA 2048+ PKCS8 private key **outside Git**, exact key ID, HTTPS issuer and named audience via `aetheris.runtime-token.pkcs8-base64`, `aetheris.runtime-token.key-id`, `aetheris.runtime-token.issuer`, `aetheris.runtime-token.audience`. Only the matching RSA **public** key would ever be pinned by Runtime, not the private half.
- Issues a distinct RS256 JWT, with `kid`, exact `iss`, single `aud`, valid persisted Identity-account `uid/sub`, existing allowlisted `Role`, only `orchestrator:read/write` scopes permitted by that role, randomly generated `jti`, and 5-minute expiry. No audience token is currently issued to users or propagated by the Gateway.
- Does not alter `JwtService`, the existing browser or Gateway HS256 contract, refresh tokens, auth endpoints, user database records or the Runtime all-API 404 quarantine. Identity alone does NOT grant tenant/project/task ownership, session revocation, workload mTLS, SSE, API actions or the ability to request an audience token.

The new `RuntimeAudienceTokenIssuerTest` uses ephemeral synthetic RSA key pairs, validates signature, header `alg/kid`, issuer, audience, UID, subject, role and read/write scope ceiling, expiration, unknown key and HMAC rejection, absent/partial/weak/corrupt operator configuration, unsaved-account refusal and existing legacy-HS256 format separation.

This is **a compatible token issuer implementation candidate**, not a verified **cross-repository round-trip** with the real Runtime verifier; a future controlled integration CI must run both exact reviewed heads, pin the corresponding public key, exercise a real nonrevoked session and demonstrate accepted signature while the Runtime Task/Mission/API HTTP filters still reject access. Never expose private key or a token-exchange endpoint prematurely. An issuance method without a session guard must remain unreachable from HTTP until reviewed.

## Strict follow-on release requirements

1. Independently design a session-bound token exchange in Platform Identity with account/session revocation, no blind scope elevation, single Runtime audience, issuer key-rotation mechanism, replay controls, and explicit accepted key history.
2. Design authenticated **Gateway-to-Runtime workload trust** and a normal application HTTP resource-server, not unsigned X-Aetheris-* identity forwarding. Keep direct-port unauthenticated traffic and currently ownerless Task/Mission data blocked.
3. Preserve existing platform clients during the opt-in transition and verify rollback. Never deploy the current Runtime S0-E4 real blanket `/api`/`/actuator` quarantine without an availability-recovery strategy.
4. Review actual production PostgreSQL owner migration, restricted roles, backup/restore, historical ownerless data and per-user SSE/mission negative tests.
5. Keep both protected main branches, certified runtime reference, runtime issue #23 / platform issue #251 open; **all nine Stage45 release gates remain NOT CERTIFIED**, physical owner-PC acceptance remains `BLOCKED_PENDING_HARDWARE`, product production remains **NO_GO**.

**No protected-main merge, no production deployment, no credentials committed, no new public HTTP route and no live customer use.** Exact completed CI on this new branch is mandatory evidence; green parent checks do not certify the candidate.
