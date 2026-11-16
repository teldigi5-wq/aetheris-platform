# B62-G2 — Read-only revocation consumer boundary (synthetic verification)

**October 26, 2026 · REVIEW-ONLY · NOT INTEGRATED.** Stacked on B62-G1 PR #302 at commit `9f286b65af1fa18c6dfa6064ebf6afe6cdccacac`. The production Gateway, User, Audit and Identity code, live customer sessions, real secrets, and database migration history are **not** modified.

## Why this slice exists

B62-G1 proved that a disposable PostgreSQL store can retain monotonic emergency JWT revocation epochs and signer-block records across parallel writes and a container restart. That result did not show whether a consumer can safely check those records, refuse SQL write privileges, or fail closed after verifying a signature. B62-G2 tests precisely those boundaries **outside** the application authentication paths.

## Test-only mechanics

The `tests/security/b62g2_signed_decision.py` adapter intentionally uses **visible synthetic HMAC keys** and standard-library cryptographic verification, never live JWT secrets. It rejects malformed or duplicate JSON members, unauthorized algorithm/kid, altered signatures, noncanonical base64url, invalid issuer/audience, missing or noninteger principal, future/not-before/expired/overlong lifetimes **before** consulting PostgreSQL. Keys come only from a two-entry static test allowlist. The production services use JJWT and do not import this Python adapter; passing these tests cannot certify the real Java filters.

The adapter performs a fresh, time-bounded read through an executable `psql` client against **only** the disposable `b62g_synthetic` Compose project. It opens a read-only SQL transaction under `b62g_verifier`, a `NOLOGIN` PostgreSQL role that can read the two prototype tables and execute one synthetic decision function but cannot raise revocation cutoffs, block keys or mutate tables. The test DBA and its synthetic password are **not** representative of a production secret-management or workload-identity design. No caller-controlled network URL, SQL identifier, raw JWT, authorization header, tenant ID or secret is interpolated into a shell command; validated numeric fields and two fixed kid literals are the only test query inputs.

Every decision is recomputed per request: positive result must be the exact boolean `True`. Exceptions, invalid output, connection failures and timeouts yield `DENY`, not `ALLOW`. This is a proof-client behavior, **not** a production guarantee. No successful result is cached. The harness exercises 11 signed/invalid token cases for each of three simulated gateway/user/audit consumer identities, both before and after restarting the disposable database. It then stops the database and requires **all** requests to fail closed, even with otherwise valid signatures. B62-G1's monotonic writer test remains separate.

## Unresolved real-service blockers — NO_GO

**NOT INTEGRATED:** The Java JWT filters do **not** query these revocation records. This test's HMAC signatures are not Identity-issued production credentials; its three `COMPONENT_NAMES` cases simulate logical consumers, not real independent Java workloads. The prototype does not establish signed principal-to-resource owner authorization or tenancy.

A reviewed production solution still needs: historical migration and recovery approval (#242), independent owner approval for emergency actions, authenticated read-only workload identities without a superuser proxy, pinned **real Java** signature/issuer/audience/kid checks before lookup, strict request-path failure denial, bounded timeouts and backpressure, real resource ownership and authorization (#251), no stale replicas, explicit primary read-after-write guarantees across all nodes, a threat model for timestamps/second-level IAT collisions, independently tested signer compromise handling and refresh family revocation, durable audit/approval, and a safe deployment/rollback rehearsal. Cross-service denial during a store outage could cause downtime, which must be planned for rather than silently bypassing revocation.

No production deployment, secret rotation, database migration, admin emergency endpoint, or approval bypass is part of this PR. Nine runtime certification gates, Stage45 `NO_GO`, and #242/#251/#255/#259 remain outstanding. The larger E3.9 and E4–E9 enterprise roadmap is separate.
