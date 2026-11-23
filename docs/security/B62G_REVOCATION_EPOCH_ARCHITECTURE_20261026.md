# B62-G — Durable emergency JWT revocation: PostgreSQL contract proof

**2026-10-26 — NOT integrated, REVIEW-ONLY**. This B62-G1 slice stacks on B62-F PR #301 exact CI-success SHA `2554378afb0951b274a54f1cea6121618fc6fc7a`. Nothing here is a real Identity database migration, API handler, runtime revocation system, production account/session change or authorized emergency switch.

## Architecture finding

B62-F demonstrated that refresh token rotation and per-token logout are scoped to opaque refresh sessions. Gateway, User and Audit continue verifying access JWTs locally; they do not query a shared emergency revocation store. Consequently neither refresh logout nor the B61 previous-key cutoff guarantees immediate revocation of a compromised current signing key or an already-issued current access JWT. Claiming otherwise would be unsafe.

## Isolated PostgreSQL prototype

The B62-G candidate resides **only in `tests/security/`** and initializes an independent, disposable PostgreSQL 16 database named `b62g_synthetic`, on an internal Docker network with no host ports and an ephemeral *named Docker volume*. The volume deliberately survives a database container restart to prove data durability, but CI unconditionally removes it with `down --volumes` and no customer data is present. It does not touch existing Flyway migrations, historical databases, live schemas, access tokens, keys, refresh sessions, or protected `main`.

Two experimental tables support only synthetic, previously signature-validated token facts:

- `principal_epochs` maps a validated positive signed principal ID to a monotonically increasing UTC signed-IAT rejection threshold. Tokens issued **at or before** the epoch are denied, including same-second issuance. A PostgreSQL row-level conflict-lock-safe `INSERT … ON CONFLICT DO UPDATE SET ... GREATEST(...)` prevents racing emergency writes from moving a cutoff backward.
- `blocked_signer_kids` records synthetic issuer + kid pairs permanently blocked for this test. A repeat block does not reset or remove the original row. The actual production signer's private key is **never** in this database. Revoking a signer while leaving it active anywhere is a design failure, not an acceptable rollback.

A synthetic post-signature SQL decision denies revoked principals/keys and invalid claim inputs, while leaving unrelated principals and correctly signed post-cutoff sessions unaffected. This function **does not verify any cryptographic signature**, kid binding, issuer authorization, JWT audience, resource owner or refresh family. Its output must NEVER be used in a deployed verifier without a separately reviewed integration.

## Evidence: real backend concurrency and recovery

The dedicated exact-HEAD workflow runs executable SQL boundary assertions in a genuine Postgres container; races ten separate `psql` writers attempting cutoffs up to 12000, then requires the persisted result to remain exactly 12000; verifies token IAT=12000 denied, IAT=12001 allowed, unrelated principal unaffected and blocked synthetic kid always denied; restarts PostgreSQL with the same ephemeral volume and rechecks results; stops PostgreSQL and confirms a deliberately fail-closed **synthetic Python/Bash decision client** returns DENY whenever the lookup fails. Every test is gated on actual process exit status, not fabricated metrics. Source/SQL/Compose safety checks and unconditional network + volume cleanup are also mandatory. No stale replica, real multi-node race or production failure is simulated here.

## Operational integration blockers — NO_GO

**NOT integrated:** there is NO request-path hook in Gateway/User/Audit, live token-version field, scoped admin approval workflow, storage ACL for a dedicated emergency writer, encrypted durable audit record, bounded decision timeout, production backup/restore, or live read-after-write consistency. The prototype allows direct access only as a synthetic DBA and cannot establish hardened production identity boundaries. **An unavailable or timed-out revocation store must fail closed** in *each* real verifier; local cache or stale replica must never permit a token whose emergency revocation has been committed. If fail-closed checks cause an outage, service availability may suffer, but that is not a license to bypass authentication.

Before any proposed adoption: secure owner approval; threat-model principal identity and cross-tenant mapping; design an append-only signed approval/audit trail and nonrevocable rollback semantics; choose strong read-after-write primary consistency across all verifier replicas; enforce short bounded RPC/SQL timeout and fail-closed semantics; conduct a real Postgres migration/backups/restore inventory under issue #242; test access JWT cryptographic signing and issuer-side stop-the-world key compromise; prove multi-node rollout, clock/IAT granularity, latency, failure injection and database restart behavior, then obtain independent security/operational signoff. A real KMS/HSM key-compromise process cannot be replaced by database flags.

Do not add an emergency public endpoint, unreviewed blanket deletes, a production app migration, or secret handling through this prototype. Stage45 remains **NO_GO**, issues #242/#251/#255/#259 are unresolved and nine runtime release-certification gates remain uncertified. Syntra E3.9/E4–E9 work continues separately; do not claim a multi-company autonomous AI production system from security CI.
