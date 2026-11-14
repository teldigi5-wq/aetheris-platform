# B62-G8 — Real PostgreSQL password-authenticated revocation role boundaries

**27 October 2026 · TEST-ONLY · REVIEW DRAFT · NOT INTEGRATED · NO_GO**

This scope continues verified B62-G7 PR #314 at `fd247cc18314e86396327f5cbf345b44473fcf65`. The production Gateway, User, Audit, Identity, migration schemas, CI secrets, runtime service keys, customer credentials and deployed authorization are **unchanged**.

## Why this test is different

B62-G1 to G4 used a synthetic PostgreSQL owner connection for database writes and / or temporarily switched to a NOLOGIN verifier role using `SET LOCAL ROLE`. That test could demonstrate SQL rules, but did **not** prove independent authenticated workload identities or separation from an emergency writer. B62-G7 used one fake HTTP authority and three real filter classes but did **not** persist policy in PostgreSQL. B62-G8 tests the missing database-authentication boundary **using real PostgreSQL TCP password logins**, not superuser impersonation.

## Isolated implementation

`tests/security/b62g8-login-roles.sql` creates exactly three standalone **LOGIN INHERIT** reader accounts — `b62g8_gateway_reader`, `b62g8_user_reader`, `b62g8_audit_reader` — all granted membership in the earlier NOLOGIN `b62g_verifier` SELECT/execute-only role. A separate `b62g8_emergency_writer` has a restricted set of synthetic INSERT/UPDATE/SELECT privileges and function execution required for monotonic principal cutoff and signer block. It cannot DELETE, create tables or call the reader-only decision function. All four accounts have obvious **test-only static passwords**, valid solely on the private, disposable `b62g_synthetic` PostgreSQL container in GitHub CI. They are NOT production credentials and must not be copied to a deployment.

The shell harness connects with `psql -h 127.0.0.1 -U <escape>role</escape>` over authenticated TCP *inside the disposable DB container* using four distinct synthetic passwords. It never selects the database superuser for reader/writer decisions, relies on the existing private Docker network with **zero published ports**, verifies the readers cannot invoke emergency writers, perform UPDATE/DELETE or create schemas/tables, and verifies the writer cannot query verifier decisions or delete blocked keys. It then commits a writer epoch, checks that **each separately authenticated reader** immediately observes the committed cutoff using the **same primary**, races eight independent writer sessions with differing cutoffs, checks monotonic retention, blocks the current synthetic signer, confirms all readers deny, restarts PostgreSQL with the isolated volume, and stops the database to verify that the **test client** returns DENY on a connection failure. The trap and unconditional CI cleanup destroy the volume/network.

## What is not proven

**NOT INTEGRATED:** This is not an active production JWT revocation system. No actual Gateway/User/Audit deployment uses these accounts or points at the database. The SQL decision helper does **not** verify signatures: real JJWT signature gates were demonstrated separately in B62-G3 through G7. The writer's table grants are broader than a hardened production emergency API; there is no audited dual-control process, no row-level tenant isolation, no credential rotation or vault integration, no TLS database channel, no malicious insider defense and no real KMS key destruction.

Same-primary read-after-write does **not** certify **read-after-write** behavior across independently deployed nodes or stale replicas, and a database container restart is not historical recovery. There is no certified clock or cross-instance workload. All real resource authorization, owner-tenant boundaries, fail-closed policy rollout and migration approvals remain unresolved.

Before production adoption: **owner approval**, historical migration / backup / restore (#242), properly provisioned least-privilege service identity, a separately controlled and audited emergency writer, strongly consistent primary reads with no **stale replicas**, read-only and emergency action isolation, independent Gateway/User/Audit authentication and object-level authorization (#251), multi-node latency/timeout and failure-injection certification, and reconciliation with parallel **certificate** trust release PR #309 and platform hardening PRs #313/#315. Stage45 **NO_GO**, nine runtime gates, and Syntra E3.9/E4–E9 remain outstanding. Do not merge/deploy these test-only passwords or SQL roles as production configuration.
