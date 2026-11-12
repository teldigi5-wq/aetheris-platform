# B62-E — Disposable real-container JWT regression and issuer startup acceptance

**26 October 2026 — REVIEW-ONLY DRAFT** stacked exactly on verified B62-D PR #299 at `8d4673be498593029b4f7158dfce799bd2a28dab`.

## Scope and rationale

B62-A through D hardened the real Gateway/User/Audit JWT filters and Identity issuer with source-level and Java tests, but the latest stack was **not** independently replayed through real nonroot API containers over internal HTTP. B62-E reuses the existing audited B61 Docker regression topology and adds a narrowly scoped live HTTP probe. This does not move any code to protected `main`, or alter real signing keys, customer data or sessions.

The separate `ttl-probe` is read-only, drops all Linux capabilities, prohibits privilege gain, mounts only *synthetic Identity-issued test JWT files*, does not use a Docker socket, and accesses exactly four fixed internal routes. Its assertions are: current RSA and previous RSA accepted during approved cutoff; a signed token with a 3,600-second lifespan accepted; 3,601-second and zero-life tokens rejected. Across User and Audit direct routes and both Gateway routes, this yields 20 concrete status-code assertions, **not** an invented benchmark or claim about owners/tenants. Prior B61 full Docker flow also reruns 72 HMAC, 32 RSA, 28 overlap, 12 explicit retirement and 40 cutoff HTTP outcomes, plus signed-JWT Java checks.

A separately built, ephemeral real Identity container uses a synthetic HMAC secret and disposable tmpfs Postgres. Its valid 3,600-second configuration must result in a functioning health endpoint. The same image must then exit unsuccessfully for both zero and 3,601 second `AETHERIS_JWT_EXPIRATION_SECONDS` settings **and** log the expected, sanitized constructor-policy reason. An unrelated startup failure must not be accepted as fail-closed issuer proof. Tests execute *after* other API probes, to avoid changing the user and audit temporary database during their assertions.

## Boundaries

The source workflow is intentionally copied from the already successful B61 full-container path, with new B62 tests, ensuring actual current source is rebuilt at the exact draft HEAD and real services are exercised. It will accept only its internally generated CI RSA public keys and HMAC fixtures. On any failure, it prints a short bounded container log and unconditionally deletes its own disposable CI containers and tmpfs volumes. No service publishes a host port or mounts production credentials.

This is not a production rollout, independently certified clock/NTP control, KMS/HSM custody validation, refresh-session migration, emergency revocation, verified object ownership, independent final approval, or physical workstation/GPU benchmark. Stage45 remains `NO_GO`, issues #242/#251/#255/#259 remain open and all nine runtime release gates are uncertified.
