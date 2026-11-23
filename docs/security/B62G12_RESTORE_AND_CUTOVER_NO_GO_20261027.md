# B62-G12 — Revocation-state restore rehearsal and hard NO_GO release admission

**27 October 2026 · REVIEW DRAFT · NOT INTEGRATED · NO_GO**

Base: B62-G11 PR #319 verified exact SHA `803ebd392ce65cc5136af0a0e535f258fb0855df`. Protected main, production Identity/Gateway/User/Audit filters, historical migration state and real credentials remain untouched.

## Why this review stage exists

B62-G1–G11 have increasingly realistic synthetic JWT and PostgreSQL proofs, including three independently authenticated reader logins, actual WAL-streamed standby and a private Docker network partition. However, a **restored old backup can erase a committed revocation and silently reinstate token access**. An owner's historical Identity/Audit **Flyway** migration (#242) and real backup/restore sign-off are not yet available, and green CI cannot waive them.

B62-G12 therefore proves two real PostgreSQL custom-format `pg_dump -Fc` / `pg_restore --exit-on-error` operations using synthetic revocation-only data and an independent password-authenticated TCP client:
1. Create a pre-incident backup while principals 42 and 43 are allowed.
2. Have a separately authenticated synthetic emergency writer commit a principal-42 cutoff 12000 and compromise block for signer `key-current`. Demonstrate actual readers now deny old or blocked synthetic tuples, while unaffected `key-safe` and newer `iat` cases remain allowed.
3. Create a *second*, post-incident backup. Restore both artifacts to **different new databases** `b62g12_stale` and `b62g12_latest`, never over the source.
4. Prove across Gateway, User and Audit **three separate password-authenticated reader logins** that the **old restore** would return ALLOW for credentials revoked on the source; the *latest* restored data preserves both signer block and cutoff, returning DENY. Missing PostgreSQL decisions are CI errors, never counted as valid DENY evidence.
5. Explicitly REVOKE `PUBLIC` database connection privileges on the restores, grant only reader CONNECT, deny intentionally wrong passwords, prohibit readers from writing restored revocations, and prove the previously privileged emergency writer has **no CONNECT to the restored databases** without a separate approved grant. This is an executable negative authorization check, not merely a SQL role definition.
6. Delete all source/restored synthetic databases, the binary backup files and private Docker network with unconditional teardown.

This backs up ONLY the `b62g_proof` schema in an isolated synthetic database. It **does not constitute a historical migration or a production recovery rehearsal**. A role/global-cluster recovery requires distinct work, and production database archival, PITR, external credentials, restore approvals, and actual row ownership are not proven.

## Machine-checkable release status never returns GO

`scripts/b62g12_release_readiness.py` is a local read-only triage tool. Its small, exact JSON contract has mandatory synthetic-evidence fields and six independent unverified production gates: **historical migration**, backup/restore owner approval, actual workload identity, audited emergency writer approval, replica consistency certification, and tenant/object authorization. It rejects missing, malformed, oversized, type-coerced or extra claims. Even with all six `production_*` fields set true, it rejects the untrusted self-assertion: local input is **not proof of owner signatures**. Its CLI **always exits nonzero**, and its best result is `NO_GO_REQUIRES_INDEPENDENT_OWNER_AND_RELEASE_APPROVAL`.

CI runs negative tests that attempt to forge approvals, omit synthetic evidence, pass extra keys, use bool-as-integer, and bypass admission. It runs the full four-module Java regression in addition to real PostgreSQL restore/privilege checks. No green test run makes the platform production-ready.

## Outstanding controls

**NOT INTEGRATED:** No active Gateway/User/Audit filter is attached to a real revocation datastore. Production integration would require independently inspected migration history and owner approval (#242), deterministic migration/checksum and rollback/restore verification, secure key/credential issuance and rotation, protected private routing, primary-only read-after-write without stale replicas, object-level tenant authorization (#251), dual-control and durable audit of emergency writer actions, reviewed request-scoped bounded timeouts, actual multi-instance signed-JWT HTTP proof, incident response playbooks, release candidate reconciliation (Java **certificate** and Compose hardening PRs #309/#313/#315) and nine runtime release gates. B62-G8's prior localhost-trust password-authentication limitation was disclosed in G9 and is avoided here by a separate `auth-probe` container with mandatory wrong-password rejection.

**Stage45 remains NO_GO.** Syntra E3.9 and E4–E9 are also incomplete. Never merge/activate automatically.
