# Separate synthetic epoch witness vs restored Audit generation — review-only negative proof

**Exact parent:** CI-verified open draft PR #397 at `162a43758ad9e0432643c54fe68608c2d2acbd8b`. **Security track:** disaster recovery generation; Aetheris Stage 45 NO_GO and XERJ HOLD.

## Motivation

PR #397 added a synthetic signed replay-admission test, but its phase/generation token and replay effects lived in the **same PostgreSQL cluster**. An administrator capable of rolling that cluster back can restore a previously `ACTIVE` marker. A signature alone does not protect against this privileged-state rollback.

This child introduces a **second separate disposable PostgreSQL16 cluster** in a unique GitHub Actions Compose project `xerj-mtls-ci-RUN_ID-ATTEMPT-witness`, bound only to `127.0.0.1:55433`. The first cluster continues at loopback 55432; RabbitMQ's existing ephemeral review vhost remains loopback 55672. These are hosted CI test fixtures, **not production infrastructure**.

### Witness boundary

`provision_xerj_disposable_witness.sh` creates a single-row test witness `xerj_epoch_witness` in its own isolated database. A freshly generated synthetic admin credential is used only for fixture setup and phase transitions; a **separate generated login** `xerj_witness_reader` can query the epoch but cannot UPDATE or DROP it. The test asserts those denials at the actual PostgreSQL privilege boundary.

`BrokerSeparateEpochWitnessIT` generates an ephemeral RSA-3072 key and authenticates a strict RS256 JWT fixture with pinned issuer/audience/kid, scope, tenant, event UUID and bounded validity. The test-side application gate queries **witness read-only** first and then makes a transactional local Audit-side idempotent effect with a local `FOR UPDATE` row lock. There is **no distributed transaction** and **no production integration**.

## Tests in the new child workflow

1. Confirm the three-database synthetic recovery fixture and the newly provisioned independent cluster exist, with distinct access credentials and only the witness reader's `SELECT` privilege.
2. With matching generation 1 across both clusters, an authenticated synthetic event inserts exactly one idempotent local effect.
3. Advance the witness to generation 2/`FROZEN` on its own cluster while Audit remains at generation 1/`ACTIVE`: old signed generation-1 capability must fail despite a local-only check that would accept it; generation-2 must be denied during the freeze.
4. Activate witness generation 2. While the Audit-local marker lags at generation 1, both old and future-generation capabilities deny. After Audit advances to generation 2, authorized generation-2 can insert one effect.
5. Simulate **Audit-only snapshot rollback** by rewinding its local generation from 2 to 1. The separately advanced witness remains generation 2. A still-valid generation-1 signature and the now-mismatched generation-2 capability both deny despite the locally stale `ACTIVE` marker.
6. Simulate witness unavailability by querying a nonexistent loopback witness port. The gate fails closed, never treating network failure as authority.
7. Send the stale signed capability through a **real disposable RabbitMQ4 queue**. Deny its readmission and negative-ACK with no requeue into a freshly created short-lived synthetic dead-letter quarantine; verify identical body and no extra Audit effect. Delete only the new queue/exchange.
8. Run the inherited Java security regression tests, previous RabbitMQ role separation, real mTLS/RS256 tenant proof, transactional outbox, database backup/restore, broker in-flight tests, review-vhost custody and previous signed replay proof. Clean up **both** independently named Compose projects with disposable volumes.

The CI also checks hosted runner identity, exact Compose project names, Bash syntax, full job success and **exact commit SHA**. It does not upload secrets or payloads. Both new workload and coordinator passwords are generated on the hosted runner.

## Unresolved and critical caveats

- **This is NOT a non-rollbackable, independent production witness.** Both disposable clusters ultimately run under the same hosted CI operator; its administrator can reset or recreate the witness database and replay signed historical tokens. No append-only external consensus/quorum, TPM/HSM attestation, trusted monotonically increasing register, root-key isolation or independently verified old-primary fencing has been established. The S-B62-A anti-rollback blocker stays OPEN.
- The replay-policy code is a **test class**, not integrated into User's transactional outbox relay, Identity, Gateway or Audit consumer. No production deployment, migration or broker ACL is changed. Read-only witness queries occur **before** a local Audit write and are not atomic with the independent cluster's generation transition. A concurrent witness change between authorization check and Audit commit remains a TOCTOU issue that cannot be certified safe by this test.
- The broker exercise proves only an isolated queue/quarantine branch inside one disposable vhost; it does not coordinate producers, old primaries, multi-vhost quorum streams, active consumers or actual durable source-custody recovery.
- Current tenant membership and workload revocation are synthetic fixtures, not production authenticated identity authority. Source PRs are review-only. No claim of exactly-once delivery or complete vulnerability remediation.
- XERJ contribution's **6-stage tracker is distinct:** only 2/6 formally complete, 4 stages remain, including genuine human privacy, licence and owner approvals and Hub validation. Aetheris Stage 45 remains **NO_GO**, with nine external attestations historically outstanding, Platform #251 OPEN and Runtime #23 OPEN.

**Decision:** successful exact-head CI would establish only a reproducible negative failure case for **Audit-only rollback while the separate witness is preserved**, not a production recovery/fencing admission. Keep child PR draft/unmerged; no production changes, upstream submission or changes to protected `main`.
