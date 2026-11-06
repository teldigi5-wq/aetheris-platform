# Aetheris RS256 authorization reconciliation — review only

Parent is Compose security draft #381 pinned at `3857d420ac5d8f57b558d1a0802179ce8dc375f7`. This child work is NOT a merge of existing RS256/mTLS PR #346.

Existing service and Gateway authorization filters are **retained as the only active filters**. They gain an explicit operator-selected, mutually exclusive `aetheris.jwt.algorithm=RS256` mode:
- Identity loads a reviewed PKCS#8 private RSA key, 2048–8192 bits, and strictly pinned `kid`; **only Identity signs tokens**.
- Gateway/User/Audit load only an X.509 RSA public key and pinned `kid`; verify actual RS256 signature, issuer, audience and existing ADMIN/scope restrictions. No untrusted JWKS fetching, algorithm autodetection, client-provided URL-based `kid`, HMAC fallback, unsigned JWT or dual-key acceptance in RS256 mode.
- Legacy explicit `HS256` remains solely for existing isolated compatibility tests and the unchanged historical Compose runtime. A selected RSA mode fails startup on an HMAC secret or missing key/kid.
- A fixed single `aetheris-api` audience is preserved at this phase for compatibility; route-specific multi-audience tokens, rotation and mTLS are **NOT YET RECONCILED**.
- New synthetic RSA-only negative tests cover forged kid, absent kid, wrong issuer/audience, wrong JWT algorithm, ordinary roles, malformed/mixed key configuration and direct User/Audit access. CI checks exactly one backend servlet auth filter.

## Operational safety
The existing three Compose variants in #381 still require `AETHERIS_JWT_SECRET` and therefore **cannot start under RSA-only configuration without a separate explicitly reviewed migration**. This preview never installs a fake signing secret into resource servers to claim completion. Real disposable integration tests from #380 remain HS256 and are **not evidence** of live RS256 network integration. That requires new isolated asymmetric-evidence CI.

Current zero-deployment review scope: no JWT credentials or private keys are committed; no production runtime, primary branch, external XERJ manifest or previously published corpus changes. Rollout must reconcile #346, implement real mTLS, route-specific audiences/rotation, real tenant ownership and replay/rollback safety, before Stage45 may change from NO_GO or XERJ from HOLD.
