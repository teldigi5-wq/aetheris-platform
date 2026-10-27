# B62-G13 — Review-only function-scoped emergency revocation and transactional audit

**27 October 2026 · REVIEW DRAFT · NOT INTEGRATED · NO_GO**

**Parent:** B62-G12 PR #320 at `312bb6a8a2f5ef7ed022a238fa6ba64b20e310ac`, exact successful CI. This new stage is *exclusively* synthetic proof and owner-review architecture; the production Identity, Gateway, User and Audit applications remain unmodified. No historic Flyway SQL, customer database, production login, real signing key, rollout toggle, or protected main is changed.

## Security defect this stage addresses

B62-G8's synthetic emergency writer was given INSERT/UPDATE table rights, which allowed bypassing the reviewed helper SQL and its future audit trail. Earlier B62-G12 showed an unapproved restore can silently restore pre-revocation authorization. Real-world incident tools should never allow a web/service writer credential to update revocation tables or delete an emergency audit directly.

B62-G13 demonstrates a narrower **function-only** design using a **separate** test writer role. It intentionally leaves older B62-G8 synthetic role intact for inherited regression compatibility, so this is NOT a production migration or retirement of prior broad grants.

## Concrete security boundaries

In disposable PostgreSQL 16, `b62g13_exec_owner` is **NOLOGIN, NOSUPERUSER, NO CREATEROLE/CREATEDB**, granted only needed table operations; `b62g13_emergency_writer` is a distinct LOGIN role that receives `EXECUTE` on two reviewed `SECURITY DEFINER` functions but **no direct table INSERT/UPDATE/DELETE/SELECT** and no audit edit rights. The functions are explicitly owned by the no-login role and use `SET search_path = pg_catalog, pg_temp` plus fully qualified SQL objects to prevent unsafe search-path resolution. Default `PUBLIC` EXECUTE is revoked in the same install transaction, before commit. Role use is authenticated over the private Docker network from a separate restricted client container, not a localhost-trust connection.

The approved *synthetic* operations are `b62g13_raise_epoch(principal, cutoff, request_id)` and `b62g13_block_signer(issuer,kid,request_id)`. Each checks signed-in session role and bounded fixture parameters. It atomically INSERTs one audit row then performs a monotonic cutoff UPSERT or permanent signer block; duplicated request IDs across either operation cause rollback. Writer cannot delete/unblock compromised keys or lower the highest cutoff. The audit reviewer is yet another independent LOGIN role with **SELECT on the synthetic audit table only**, no DML or revocation-table privileges. CI tests denied wrong passwords for both writer and auditor; reader denied emergency operations; writer denied 14 direct table/DDL/verifier operations; auditor cannot edit audit or query private revocation tables; and actual valid writer operations are read by three separate password-authenticated service readers. Replay/invalid-request attempts must leave both policy and audit event count unchanged.

**This audit is not tamper-proof.** A database owner, privileged database operator or restore operation can edit or erase the database records. Synthetic request IDs are not signed approvals, authenticated actor is a single role, and the demo does **not** prove immutable off-database append-only logging, operator identity federation, time synchronization or dual-control. A real audited emergency interface would need centrally authenticated and independently verified human **owner approval**, dual-control, out-of-band log forwarding, nonrepudiable event provenance, incident retention, KMS key custody and rollback/restore reconciliation.

## Remaining owner-reviewable production integration gates

- **Historical migration and restore (#242):** Identify actual Identity/Audit/User DB ownership, Flyway checksums, schema history and baseline rules, apply only after DBA-approved versioned migrations and representative restore. G13 SQL is **not** a Flyway migration.
- **Real service identity and read-after-write:** Issue, scope, rotate and revoke distinct workload authentication; protect transport; ensure non-stale primary **read-after-write** for Gateway, User and Audit and deny any network/read error. No production JDBC or reactive client exists yet.
- **Emergency audit and authorization:** Require human dual-control and durable external audit before issuing any real signer/UID revocation; prevent a compromised signer from minting post-cutoff tokens; never expose anonymous emergency functions or frontend secrets.
- **Independent tenant/object authorization (#251):** JWT `uid` alone is not authorization to access a database row; verify resource ownership and ADMIN policy in each service separately.
- **Release supply chain and operational control:** Reconcile **certificate** trust and Compose hardening drafts #309/#313/#315, image provenance and remediation #255/#259, staged rollback and nine runtime certification gates. Owner security acceptance and Stage45 are distinct from CI success.

**NOT INTEGRATED. NO_GO.** No active Gateway/User/Audit filter uses these functions. Passing tests establishes only a synthetic least-privilege boundary. E3.9 / E4–E9 remain unfinished; 13 Stage45 safety blockers and nine uncertified gates remain separately tracked.
