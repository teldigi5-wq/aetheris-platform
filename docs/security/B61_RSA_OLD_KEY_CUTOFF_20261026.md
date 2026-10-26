# B61 — Mandatory previous RSA key cutoff, enforced per request

**26 October 2026 — REVIEW-ONLY DRAFT, stacked on B60 [PR #294](https://github.com/teldigi5-wq/aetheris-platform/pull/294). NO merge, production secrets, deployed runtime, customer database, current issuer private key or live session changes.**

## Reason for B61

B60 demonstrates a bounded **two-public-key** RS256 overlap in actual Gateway/User/Audit services and explicit manual removal of the previous verification key. But in B60 the previous key can remain accepted indefinitely if a deployed service is not reconfigured. **B61 changes the real JWT verifier source** so the presence of any previous RSA public key requires a strict, UTC, absolute retirement deadline, enforced **at every old-key request**, even if the configured key still exists or the process has not restarted.

## API and fail-closed policy

A new optional environment setting on Gateway, User and Audit only:

`AETHERIS_JWT_PREVIOUS_KEY_NOT_AFTER_EPOCH_SECONDS` — a **10-digit positive decimal Unix timestamp in UTC seconds**. It MUST accompany the existing previous public RSA X.509 key and distinct allowlisted previous kid; having a previous key/kid without deadline, or having a deadline without previous key/kid, makes service startup fail. `AETHERIS_JWT_MODE=hmac` rejects any extra previous-key or deadline configuration.

When two keys are configured in RS256 mode:
- The previous public key must be distinct from the active one, RSA modulus **3072+ bits**, and its signed kid must match the key that really verified the RS256 signature; ordinary signed issuer, exclusive API audience, role, scope and UID checks continue.
- At startup, the absolute previous-key cutoff cannot be **more than 3600 seconds** in the future. This caps the review candidate's per-deployment overlap; no dynamic JWT-header-specified key or clock can relax it. The service's UTC `Clock.systemUTC()` is independent of incoming HTTP data.
- At **and after** the cutoff instant, previous-key verification is denied (HTTP 401), even when the old public key is still in memory and configuration and its JWT has not reached its own `exp`. Current-key JWTs continue to work.
- An **already expired** but otherwise valid cutoff does not prevent startup or current-key traffic: it makes the old key unusable immediately. Operators should still remove the old key from configuration; the deadline guards against missed or delayed cleanup, but does not physically destroy the public key.
- The filter checks the cutoff before attempting previous-key verification **and again after actual signature validation**, closing any expiry race within the request.
- No automatic token revocation lists, JWK fetching, mutable key discovery, real key rollover schedule, asymmetric issuer private-key change, refresh-session migration or cryptographic-key destruction is introduced.

## CI test contract

1. All inherited B60 Python and Maven tests run against the **exact draft PR head**, including its genuine Identity-issued JWT test and real 72 HMAC-mode + 32 RSA single-public-key + 28 B60 overlap + 12 B60 explicit retirement HTTP regressions.
2. B61 adds eight source/configuration security regression checks and three genuine Gateway/User/Audit **Java integration suites** (three tests each), using the same Identity-generated synthetic RSA keys. A clock injected only in tests is advanced past the cutoff **within the very same real filter instance**: an old signed token changes from accepted to HTTP 401 at that instant, and the same correctly signed current-key token remains valid. Missing, stale, malformed, far-future (beyond 3600 seconds) and HMAC-mixed trust configurations are exercised.
3. The B60 Docker overlap and explicit retirement Compose overlays are hardened to include and remove the cutoff consistently. A B61-only third overlay installs a near-term cutoff while keeping both public keys in actual non-root Corretto 21.0.13 Gateway/User/Audit containers. Before cutoff, 28 real overlap HTTP assertions must succeed; the workflow then waits for the same process's UTC deadline without restarting it, confirms actual Docker container IDs are unchanged, and reruns 12 real HTTP assertions requiring old tokens to return 401 while current tokens still succeed. This is **40 additional live deadline-boundary HTTP outcomes** distinct from B60's explicit key removal.
4. The workflow uses an internal Docker-only network, no host ports, no customer or persistent databases (Postgres data is in tmpfs), read-only no-privilege probe and no private issuer key outside the synthetic test JVM. It cleans up its own disposable containers, volumes and network unconditionally. No real key files are uploaded.

## Operational and security limitations

A valid cutoff **alone is not sufficient production-ready key rotation**. A coordinated rollout requires real source-of-truth UTC/NTP clock reliability across services, immutable signed release deployments, synchronized Identity issuing vs API accepting modes, approved access-token maximum age and clock skew, live session refresh/renewal policy, user/agent compatibility, real KMS/HSM private key custody, safe public verifier distribution, incident response and rollback. Old-key tokens cannot be renewed or migrated by B61 automatically.

Changing the deadline value and restarting a service could technically grant a **new future** overlap (up to the one-hour per-start cap). B61 does not have a persistent, independently attested rollover epoch or organization-wide maximum extension policy, so the cutoff must be change-controlled and externally audited. A compromised host clock also defeats wall-clock policy until time integrity is restored. Do not describe this as irreversible revocation or automated operator compliance.

**Open blockers:** #251 real resource ownership and workload identities; #242 owner-provided historical DBA restore; #255 deployed image acceptance; #259 final advisory/SBOM provenance; nine runtime certification gates and Stage45 remain **NO_GO**. A passing synthetic CI workflow is not production authorization.
